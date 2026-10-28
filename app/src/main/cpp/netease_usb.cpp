// CurrentMusic adapter for the unmodified, SHA-256-pinned NetEase 9.6.15 arm64
// USB engine. Recovered ABI and restrictions: NETEASE_USB_OUTPUT.md.
// No player/JNI registration, account APIs, or binary patches are used.
#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include "netease_usb_format.h"
#include "netease_usb_status.h"
#include <android/log.h>

namespace {
struct AudioFormat {
    uint8_t channels, bits;
    uint16_t reserved = 0;
    uint32_t rate, sampleFormat;
};
static_assert(sizeof(AudioFormat) == 12 && offsetof(AudioFormat, rate) == 4);
template<class T> T symbol(void* library, const char* name) {
    auto value = reinterpret_cast<T>(dlsym(library, name));
    if (!value) throw std::runtime_error("[USB_DRIVER_ABI] missing native interface");
    return value;
}
struct Api {
    void* library = dlopen("libncmusbaudio.so", RTLD_NOW | RTLD_LOCAL);
    void* (*manager)();
    void (*use)(void*, int);
    std::shared_ptr<void> (*create)(void*, int);
    void (*discard)(void*);
    bool (*supports)(void*, AudioFormat, int&);
    int (*select)(void*, int, AudioFormat);
    void (*floatInput)(void*, bool);
    int (*start)(void*);
    int (*pause)(void*);
    int (*resume)(void*);
    bool (*push)(void*, uint8_t*, unsigned);
    int (*leftMs)(void*);
    int (*aheadMs)(void*);
    int (*status)(void*);
    bool (*lost)(void*);
    void* (*usbDevice)(void*);
    int (*getConfig)(void*, const currentmusic_usb::Config**);
    void (*freeConfig)(const currentmusic_usb::Config*);
    void* (*getInterface)(void*, uint8_t);
    bool (*isOutput)(void*);
    std::vector<currentmusic_usb::StreamingInfo> (*infos)(void*);
    bool (*suggest)(void*, AudioFormat, AudioFormat&, int&, bool);
    Api() {
        if (!library) throw std::runtime_error("[USB_DRIVER_LOAD] NetEase USB engine unavailable");
        try {
#define LOAD(field, name) field = symbol<decltype(field)>(library, name)
        LOAD(manager, "_ZN3Usb13DeviceManager9sharedMgrEv");
        LOAD(use, "_ZN3Usb13DeviceManager9UseDeviceEi");
        LOAD(create, "_ZN3Usb13DeviceManager12CreateDeviceEi");
        LOAD(discard, "_ZN3Usb13DeviceManager13DiscardDeviceEv");
        LOAD(supports, "_ZN3Usb15AudioDeviceImpl20IsSupportAudioFormatENS_11AudioFormatERi");
        LOAD(select, "_ZN3Usb15AudioDeviceImpl17SelectAudioFormatEiNS_11AudioFormatE");
        LOAD(floatInput, "_ZN3Usb15AudioDeviceImpl11EnableFloatEb");
        LOAD(start, "_ZN3Usb15AudioDeviceImpl5StartEv");
        LOAD(pause, "_ZN3Usb15AudioDeviceImpl5PauseEv");
        LOAD(resume, "_ZN3Usb15AudioDeviceImpl6ResumeEv");
        LOAD(push, "_ZN3Usb15AudioDeviceImpl4PushEPhj");
        LOAD(leftMs, "_ZN3Usb15AudioDeviceImpl15GetLeftDataTimeEv");
        LOAD(aheadMs, "_ZN3Usb15AudioDeviceImpl16getDeviceAheadMsEv");
        LOAD(status, "_ZN3Usb15AudioDeviceImpl13GetXferStatusEv");
        LOAD(lost, "_ZN3Usb15AudioDeviceImpl12IsDeviceLostEv");
        LOAD(usbDevice, "_ZN3Usb15AudioDeviceImpl9getDeviceEv");
        LOAD(getConfig, "libusb_get_active_config_descriptor");
        LOAD(freeConfig, "libusb_free_config_descriptor");
        LOAD(getInterface, "_ZN3Usb15AudioDeviceImpl21getStreamingInterfaceEh");
        LOAD(isOutput, "_ZN3Usb23AudioStreamingInterface8IsOutputEv");
        LOAD(infos, "_ZN3Usb23AudioStreamingInterface8GetInfosEv");
        LOAD(suggest, "_ZN3Usb15AudioDeviceImpl21GetSuggestAudioFormatENS_11AudioFormatERS1_Rib");
#undef LOAD
        } catch (...) { dlclose(library); throw; }
    }
};
Api& api() { static Api value; return value; }
struct Stream {
    void* manager = nullptr;
    std::shared_ptr<void> device;
    int fd = -1, rate = 0, frameBytes = 0;
    int64_t bytes = 0, position = 0;
    std::chrono::steady_clock::time_point emptySince{};
    bool playing = false;
    int lastTransferStatus = -999;
    ~Stream() {
        // Destroy engine-owned device references before exiting libusb. The engine
        // stops its streaming/event threads and releases claimed USB interfaces.
        device.reset();
        if (manager) api().discard(manager);
        if (fd >= 0) ::close(fd);
    }
};
std::mutex mutex;
std::unique_ptr<Stream> active;
int64_t generation = 0;
std::string diagnostics;
void record(const std::string& line) {
    if (diagnostics.size() < 48000) diagnostics += line + "\n";
    __android_log_write(ANDROID_LOG_INFO, "CurrentMusicAudio", line.c_str());
}
std::vector<currentmusic_usb::StreamingInfo> candidates(void* device) {
    auto& a = api();
    const currentmusic_usb::Config* raw = nullptr;
    if (a.getConfig(a.usbDevice(device), &raw) != 0 || !raw)
        throw std::runtime_error("[USB_CAPABILITIES] cannot read active USB configuration");
    std::unique_ptr<const currentmusic_usb::Config, decltype(a.freeConfig)> config(raw, a.freeConfig);
    if (!config->interfaces && config->interfaceCount) throw std::runtime_error("[USB_CAPABILITIES] invalid USB configuration");
    std::vector<currentmusic_usb::StreamingInfo> result;
    // Same order as AudioDeviceImpl::initAudioStreamingInterfaces and
    // setupOutputAudioStreamingInfo. Sorting interface IDs would corrupt indices.
    for (int i = 0; i < config->interfaceCount; ++i) {
        const auto& group = config->interfaces[i];
        if (group.count < 1 || !group.alternate) continue;
        const auto& first = group.alternate[0];
        if (first.deviceClass != 1 || first.subclass != 2) continue;
        auto face = a.getInterface(device, first.number);
        if (!face || !a.isOutput(face)) continue;
        auto entries = a.infos(face);
        result.insert(result.end(), entries.begin(), entries.end());
    }
    record("usbHost.supported.count=" + std::to_string(result.size()));
    for (size_t i = 0; i < result.size(); ++i) {
        const auto& info = result[i];
        auto out = "usbHost.supported[" + std::to_string(i) + "]=interface=" + std::to_string(info.interfaceId) +
            " alt=" + std::to_string(info.alternate) + " channels=" + std::to_string(info.channels) +
            " validBits=" + std::to_string(info.validBits) + " subslotBytes=" + std::to_string(info.subslotBytes) +
            " uacFormat=" + std::to_string(info.uacFormat) + " rates=[";
        for (size_t n = 0; n < info.rates.size() && n < 64; ++n) { if (n) out += ','; out += std::to_string(info.rates[n]); }
        if (info.rates.size() > 64) out += ",...(" + std::to_string(info.rates.size()) + " total)";
        out += ']'; record(out);
    }
    return result;
}
void fail(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
Stream& stream(jlong id, const char* stage = "verify") {
    if (!active || id != generation) throw std::runtime_error("[USB_CLOSED] output already released");
    auto& s = *active;
    const bool detached = api().lost(s.device.get());
    const int status = api().status(s.device.get());
    if (status != s.lastTransferStatus || detached) {
        record("usbHost.transfer stage=" + std::string(stage) + " raw=" + std::to_string(status) +
            " state=" + currentmusic_usb::transferLabel(status) + " detached=" + std::to_string(detached));
        s.lastTransferStatus = status;
    }
    if (currentmusic_usb::transferHealthy(status, detached)) return s;
    if (detached) throw std::runtime_error("[USB_DISCONNECTED] native driver reports DAC detached; transferStatus=" + std::to_string(status));
    switch (currentmusic_usb::transferState(status)) {
        case currentmusic_usb::TransferState::Idle:
        case currentmusic_usb::TransferState::Running: break;
        case currentmusic_usb::TransferState::Timeout:
            throw std::runtime_error("[USB_TIMEOUT] all native transfers timed out; transferStatus=2");
        case currentmusic_usb::TransferState::Error:
            throw std::runtime_error("[USB_TRANSFER_ERROR] native transfers failed; transferStatus=3");
        default: throw std::runtime_error("[USB_STATUS_UNKNOWN] unrecognized native transfer status=" + std::to_string(status));
    }
    return *active;
}
int64_t position(Stream& s) {
    // Engine queue/in-flight estimates, not a DAC hardware timestamp.
    auto duration = s.bytes / s.frameBytes * 1000000 / s.rate;
    auto queued = std::max(0, api().leftMs(s.device.get())) + std::max(0, api().aheadMs(s.device.get()));
    auto next = std::max<int64_t>(0, duration - int64_t(queued) * 1000);
    // Both engine counters truncate to milliseconds. A zero estimate must settle
    // before exposing the final byte as drained, otherwise Media3 could close the
    // device with the final fractional-millisecond USB packet still in flight.
    if (duration > 0 && queued == 0) {
        auto now = std::chrono::steady_clock::now();
        if (s.emptySince == std::chrono::steady_clock::time_point{}) s.emptySince = now;
        if (now - s.emptySince < std::chrono::milliseconds(20)) next = duration - 1;
    } else s.emptySince = {};
    s.position = std::max(s.position, next);
    return s.position;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_checkAbi(JNIEnv* env, jobject) {
    try { api(); return true; } catch (const std::exception& error) { fail(env, error); return false; }
}
#ifndef NDEBUG
// Exercises the production selection predicate with native fixtures, no USB I/O.
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_testFormatSelection(JNIEnv*, jobject) {
    using namespace currentmusic_usb;
    StreamingInfo i;
    i.channels = 2; i.validBits = 24; i.subslotBytes = 4; i.uacFormat = 1; i.rates = {44100, 48000, 192000};
    if (!accepts(i, 192000, 2, 4, 24) || accepts(i, 192000, 2, 4, 32)) return false;
    if (accepts(i, 96000, 2, 4, 24) || accepts(i, 192000, 1, 4, 24)) return false;
    i.subslotBytes = 3;
    if (accepts(i, 192000, 2, 4, 24)) return false;
    i.subslotBytes = 4; i.uacFormat = 3;
    if (accepts(i, 192000, 2, 4, 24)) return false;
    i.uacFormat = 1;
    auto wider = i; wider.validBits = 32;
    if (select({i, wider}, 192000, 2, 4, 24) != 1 || select({i}, 192000, 2, 4, 32) != -1) return false;
    auto lower = i; lower.rates = {48000};
    if (select({lower, i}, 192000, 2, 4, 24) != 1) return false;
    return true;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_testTransferState(JNIEnv*, jobject, jint raw, jboolean detached) {
    return currentmusic_usb::transferHealthy(raw, detached == JNI_TRUE);
}
#endif
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_open(JNIEnv* env, jobject, jint fd, jint rate, jint channels, jint encoding, jint sourceBits) {
    std::lock_guard<std::mutex> lock(mutex);
    try {
        diagnostics.clear();
        record("usbHost.adapter=ncm-usb-v3-status-init");
        if (active) throw std::runtime_error("[USB_BUSY] another exclusive stream owns the DAC");
        if (fd < 0 || rate < 8000 || rate > 192000 || channels < 1 || channels > 2 || (encoding != 2 && encoding != 22) ||
            (sourceBits != 16 && sourceBits != 24 && sourceBits != 32) || sourceBits > (encoding == 2 ? 16 : 32))
            throw std::runtime_error("[USB_PCM] this pinned adapter accepts only native 16/32-bit integer PCM, mono/stereo, 8-192 kHz");
        auto next = std::make_unique<Stream>();
        next->fd = dup(fd);
        if (next->fd < 0) throw std::runtime_error("[USB_FD] could not retain authorized USB descriptor");
        auto& a = api();
        next->manager = a.manager();
        if (!next->manager) throw std::runtime_error("[USB_DRIVER_INIT] could not initialize USB manager");
        a.use(next->manager, next->fd);
        next->device = a.create(next->manager, next->fd);
        if (!next->device) throw std::runtime_error("[USB_OPEN] engine could not open the authorized USB device");
        AudioFormat format{uint8_t(channels), uint8_t(encoding == 2 ? 16 : 32), 0, uint32_t(rate), uint32_t(encoding == 2 ? 1 : 2)};
        const auto all = candidates(next->device.get());
        int shortcutIndex = -1;
        const bool shortcut = a.supports(next->device.get(), format, shortcutIndex);
        record("usbHost.shortcut.accepted=" + std::to_string(shortcut) + " index=" + std::to_string(shortcutIndex));
        const int index = currentmusic_usb::select(all, rate, channels, encoding == 2 ? 2 : 4, sourceBits);
        if (index < 0) {
            AudioFormat suggested{}; int suggestedIndex = -1;
            if (a.suggest(next->device.get(), format, suggested, suggestedIndex, false)) {
                record("usbHost.suggestion=index=" + std::to_string(suggestedIndex) + " rate=" + std::to_string(suggested.rate) +
                    " channels=" + std::to_string(suggested.channels) + " bits=" + std::to_string(suggested.bits) + " applied=false");
            }
            throw std::runtime_error("[USB_FORMAT] USB candidates do not preserve requested rate, channels, sample precision and container; see supported configurations in diagnostics");
        }
        const auto& chosen = all[index];
        record("usbHost.selected=index=" + std::to_string(index) + " rate=" + std::to_string(rate) +
            " sourceBits=" + std::to_string(sourceBits) + " validBits=" + std::to_string(chosen.validBits) +
            " containerBytes=" + std::to_string(chosen.subslotBytes) + " shortcutBypassed=" + std::to_string(!shortcut));
        if (a.select(next->device.get(), index, format) != 0)
            throw std::runtime_error("[USB_NEGOTIATION] USB interface claim or sample-rate negotiation failed");
        a.floatInput(next->device.get(), false);
        // Start clears the ring. Do it before accepting any PCM, then pause. Later
        // play uses Resume, so audio queued while paused is preserved.
        const int started = a.start(next->device.get());
        record("usbHost.initialize stage=start return=" + std::to_string(started));
        if (started != 0) throw std::runtime_error("[USB_START] native Start failed; result=" + std::to_string(started));
        const int paused = a.pause(next->device.get());
        record("usbHost.initialize stage=pause-after-start return=" + std::to_string(paused));
        if (paused != 0) throw std::runtime_error("[USB_INIT_PAUSE] native initial Pause failed; result=" + std::to_string(paused));
        next->rate = rate;
        next->frameBytes = channels * (encoding == 2 ? 2 : 4);
        active = std::move(next);
        const auto id = ++generation;
        try { stream(id, "start-paused"); } catch (...) { active.reset(); throw; }
        return id;
    } catch (const std::exception& error) { fail(env, error); return 0; }
}
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_diagnostics(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(mutex);
    auto result = env->NewStringUTF(diagnostics.c_str());
    if (result) diagnostics.clear();
    return result;
}
extern "C" JNIEXPORT jint JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_write(JNIEnv* env, jobject, jlong id, jobject buffer, jint offset, jint count) {
    std::lock_guard<std::mutex> lock(mutex);
    try {
        auto& s = stream(id, "write");
        auto data = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
        auto capacity = env->GetDirectBufferCapacity(buffer);
        if (!data || offset < 0 || count < 0 || int64_t(offset) + count > capacity || count % s.frameBytes != 0)
            throw std::runtime_error("[USB_BUFFER] invalid PCM buffer bounds or frame alignment");
        // Pinned SelectAudioFormat guarantees >=512 KiB. At the admitted maximum
        // 192 kHz/stereo/32 bit, 100 ms + one 8192-byte write + rounding is <164 KiB.
        // One serialized producer and no other callers can overrun this ring. Push
        // reports bool rather than consumed bytes, so never feed an unbounded block.
        if (api().leftMs(s.device.get()) >= 100) return 0;
        auto accepted = std::min(count, 8192 / s.frameBytes * s.frameBytes);
        if (accepted == 0) return 0;
        if (!api().push(s.device.get(), data + offset, accepted))
            throw std::runtime_error("[USB_WRITE] native USB ring rejected PCM");
        s.bytes += accepted;
        s.emptySince = {};
        return accepted;
    } catch (const std::exception& error) { fail(env, error); return -1; }
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_play(JNIEnv* env, jobject, jlong id, jboolean playing) {
    std::lock_guard<std::mutex> lock(mutex);
    try {
        auto& s = stream(id, playing ? "resume" : "pause");
        if (s.playing == bool(playing)) return;
        if ((playing ? api().resume(s.device.get()) : api().pause(s.device.get())) != 0)
            throw std::runtime_error("[USB_STATE] native pause/resume failed");
        s.playing = playing;
    } catch (const std::exception& error) { fail(env, error); }
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_position(JNIEnv* env, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(mutex);
    try { return position(stream(id)); } catch (const std::exception& error) { fail(env, error); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_verify(JNIEnv* env, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(mutex);
    try { stream(id); } catch (const std::exception& error) { fail(env, error); }
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_currencortex_music_core_media_NeteaseUsbBridge_close(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(mutex);
    if (active && id == generation) active.reset();
}
