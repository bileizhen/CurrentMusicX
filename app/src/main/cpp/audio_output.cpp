#include <jni.h>
#include <oboe/Oboe.h>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <memory>
#include <vector>
#include <cmath>
#include <mutex>
#include "audio_output_registry.h"

// One Media3 playback-thread producer and one realtime callback consumer. No allocation,
// mutex, JNI, logging or blocking operations occur in the audio callback.
struct OutputState final : oboe::AudioStreamDataCallback, oboe::AudioStreamErrorCallback {
    std::vector<int16_t> ring;
    std::atomic<int64_t> read{0}, written{0}, consumed{0};
    std::atomic<int64_t> lastMediaFrame{0};
    int64_t callbackFrames = 0;
    std::atomic<float> gain{1};
    std::atomic<int> error{0};
    int channels, rate;
    int64_t lastPosition = 0;
    explicit OutputState(int c, int r, int bufferMs) : ring((int64_t(r) * bufferMs + 999) / 1000 * c), channels(c), rate(r) {}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        auto *out = static_cast<int16_t*>(data);
        const int64_t start = read.load(std::memory_order_relaxed);
        const int64_t count = std::min<int64_t>(frames * channels,
            written.load(std::memory_order_acquire) - start);
        const float volume = gain.load(std::memory_order_relaxed);
        for (int64_t i = 0; i < count; ++i) out[i] = static_cast<int16_t>(ring[(start+i) % ring.size()] * volume);
        std::fill(out + count, out + frames * channels, 0);
        read.store(start + count, std::memory_order_release);
        consumed.fetch_add(count / channels, std::memory_order_relaxed);
        if (count > 0) lastMediaFrame.store(callbackFrames + count / channels, std::memory_order_release);
        callbackFrames += frames;
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result result) override { error.store(static_cast<int>(result)); }
};
struct Output {
    std::mutex control;
    // Oboe retains callbacks across its asynchronous disconnect handling. They own
    // only PCM state, not the stream, so release cannot cause a use-after-free or cycle.
    std::shared_ptr<OutputState> state;
    std::shared_ptr<oboe::AudioStream> stream;
    Output(int c, int r, int bufferMs) : state(std::make_shared<OutputState>(c, r, bufferMs)) {}
    ~Output() { if (stream) stream->close(); }
};
static currentmusic_audio::HandleRegistry<Output> outputs;
static constexpr int kReleased = -899;
#define JNI(name) Java_io_github_currencortex_music_core_media_NativeAudioBridge_##name
extern "C" JNIEXPORT jlong JNICALL JNI(open)(JNIEnv*, jobject, jint api, jint rate, jint channels, jint device, jboolean exclusive, jint softwareBufferMs) {
    if (api < 0 || api > 2 || rate < 4000 || rate > 384000 || channels < 1 || channels > 8 ||
        softwareBufferMs < 20 || softwareBufferMs > 2000 || device < 0) return 0;
    auto output = std::make_shared<Output>(channels, rate, softwareBufferMs);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)->setFormat(oboe::AudioFormat::I16)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None)
        ->setFormatConversionAllowed(false)->setChannelConversionAllowed(false)
        ->setChannelCount(channels)->setSampleRate(rate)->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(exclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
        ->setAudioApi(api == 1 ? oboe::AudioApi::OpenSLES : api == 2 ? oboe::AudioApi::AAudio : oboe::AudioApi::Unspecified)
        ->setDeviceId(device)->setDataCallback(output->state)->setErrorCallback(output->state);
    // Conversion is deliberately disabled: Media3 owns PCM resampling and the output
    // must either accept that format or report a fallback, never silently relabel it.
    if (builder.openStream(output->stream) != oboe::Result::OK) return 0;
    if (output->stream->getFormat() != oboe::AudioFormat::I16 ||
        output->stream->getSampleRate() != rate || output->stream->getChannelCount() != channels ||
        (api == 1 && output->stream->getAudioApi() != oboe::AudioApi::OpenSLES) ||
        (api == 2 && output->stream->getAudioApi() != oboe::AudioApi::AAudio) ||
        (device > 0 && output->stream->getAudioApi() == oboe::AudioApi::AAudio &&
            output->stream->getDeviceId() > 0 && output->stream->getDeviceId() != device) ||
        (exclusive && output->stream->getSharingMode() != oboe::SharingMode::Exclusive)) return 0;
    output->stream->setBufferSizeInFrames(output->stream->getFramesPerBurst() * 2);
    return outputs.add(std::move(output));
}
extern "C" JNIEXPORT jint JNICALL JNI(write)(JNIEnv* env, jobject, jlong h, jobject buffer, jint offset, jint bytes) {
    const auto output = outputs.get(h);
    if (!output) return kReleased;
    std::lock_guard<std::mutex> lock(output->control);
    auto* o = output->state.get();
    if (o->error.load() != 0) return o->error.load();
    auto* base = static_cast<int16_t*>(env->GetDirectBufferAddress(buffer));
    const auto capacity = env->GetDirectBufferCapacity(buffer);
    if (!base || offset < 0 || bytes < 0 || offset + static_cast<int64_t>(bytes) > capacity ||
        offset % (o->channels * 2) != 0 || bytes % (o->channels * 2) != 0) return -1;
    auto written = o->written.load(std::memory_order_relaxed);
    int64_t count = std::min<int64_t>(bytes / 2, o->ring.size() - (written - o->read.load(std::memory_order_acquire)));
    count -= count % o->channels;
    auto* input = reinterpret_cast<int16_t*>(reinterpret_cast<char*>(base) + offset);
    for (int64_t i = 0; i < count; ++i) o->ring[(written+i) % o->ring.size()] = input[i];
    o->written.store(written + count, std::memory_order_release);
    return static_cast<jint>(count * 2);
}
extern "C" JNIEXPORT void JNICALL JNI(play)(JNIEnv*, jobject, jlong h) {
    const auto o = outputs.get(h); if (!o) return;
    std::lock_guard<std::mutex> lock(o->control);
    const auto r = o->stream->requestStart(); if(r != oboe::Result::OK) o->state->error.store(static_cast<int>(r));
}
extern "C" JNIEXPORT void JNICALL JNI(pause)(JNIEnv*, jobject, jlong h) {
    const auto o = outputs.get(h); if (!o) return;
    std::lock_guard<std::mutex> lock(o->control);
    const auto r = o->stream->pause(); if(r != oboe::Result::OK) o->state->error.store(static_cast<int>(r));
}
extern "C" JNIEXPORT void JNICALL JNI(close)(JNIEnv*, jobject, jlong h) { outputs.remove(h); }
extern "C" JNIEXPORT void JNICALL JNI(volume)(JNIEnv*, jobject, jlong h, jfloat gain) {
    const auto o = outputs.get(h); if (o) o->state->gain.store(std::isfinite(gain) ? std::clamp(gain,0.f,1.f) : 0.f);
}
extern "C" JNIEXPORT jlong JNICALL JNI(position)(JNIEnv*, jobject, jlong h) {
    const auto o = outputs.get(h); if (!o) return 0;
    std::lock_guard<std::mutex> lock(o->control);
    auto* state=o->state.get();
    // Trailing silence must not hold the media clock short of EOS forever.
    const auto pending=std::max<int64_t>(0,state->lastMediaFrame.load(std::memory_order_acquire)-o->stream->getFramesRead());
    state->lastPosition=std::max(state->lastPosition,std::max<int64_t>(0,state->consumed.load()-pending));
    return state->lastPosition * 1000000 / state->rate;
}
extern "C" JNIEXPORT jint JNICALL JNI(api)(JNIEnv*, jobject, jlong h) {
    const auto o = outputs.get(h); return !o ? 0 : o->stream->getAudioApi() == oboe::AudioApi::AAudio ? 2 : 1;
}
extern "C" JNIEXPORT jint JNICALL JNI(error)(JNIEnv*, jobject, jlong h) { const auto o = outputs.get(h); return o ? o->state->error.load() : kReleased; }
extern "C" JNIEXPORT jlong JNICALL JNI(bufferFrames)(JNIEnv*, jobject, jlong h) {
    const auto o = outputs.get(h); return o ? static_cast<jlong>(o->state->ring.size() / o->state->channels) : 0;
}
extern "C" JNIEXPORT jlongArray JNICALL JNI(snapshot)(JNIEnv* env, jobject, jlong h) {
    const auto o = outputs.get(h); if (!o) return nullptr;
    std::lock_guard<std::mutex> lock(o->control);
    const auto xruns = o->stream->getXRunCount();
    const auto latency = o->stream->calculateLatencyMillis();
    // Latency is the timestamp-derived platform stream estimate, excluding this
    // adapter's software ring and unknown DAC/Bluetooth acoustic delay.
    const auto micros = latency && std::isfinite(latency.value()) && latency.value() >= 0 && latency.value() <= 60000
        ? static_cast<jlong>(latency.value() * 1000) : -1;
    const jlong values[] = {
        o->stream->getAudioApi() == oboe::AudioApi::AAudio ? 2 : 1,
        o->stream->getSampleRate(), o->stream->getChannelCount(),
        o->stream->getFormat() == oboe::AudioFormat::I16 ? 2 : 0,
        o->stream->getDeviceId(), o->stream->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0,
        o->stream->getBufferSizeInFrames(), o->stream->getBufferCapacityInFrames(),
        o->stream->getFramesPerBurst(), static_cast<jlong>(o->state->ring.size() / o->state->channels),
        xruns ? xruns.value() : -1, micros, o->state->error.load()
    };
    auto result = env->NewLongArray(sizeof(values) / sizeof(values[0]));
    if (result) env->SetLongArrayRegion(result, 0, sizeof(values) / sizeof(values[0]), values);
    return result;
}
