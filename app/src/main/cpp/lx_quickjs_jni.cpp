#include <jni.h>

#include <chrono>
#include <cstddef>
#include <cstdlib>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

#include "quickjs/quickjs.h"

namespace {

using Clock = std::chrono::steady_clock;
constexpr size_t kHeapLimit = 64 * 1024 * 1024;
constexpr size_t kStackLimit = 256 * 1024;
constexpr size_t kTransferLimit = 8 * 1024 * 1024;
constexpr auto kEvaluationTimeout = std::chrono::seconds(2);

struct Engine {
    std::mutex mutex;
    JSRuntime *runtime = nullptr;
    JSContext *context = nullptr;
    Clock::time_point deadline;
    const char *budgetError = nullptr;

    ~Engine() {
        if (context) JS_FreeContext(context);
        if (runtime) JS_FreeRuntime(runtime);
    }

    bool BudgetExceeded() {
        if (!budgetError && Clock::now() >= deadline) {
            budgetError = "QuickJS deadline exceeded";
        }
        return budgetError != nullptr;
    }
};

// Charge the allocator header as well as the requested size, before allocating.
struct alignas(std::max_align_t) Allocation {
    size_t size;
};

size_t AllocationSize(const void *ptr) {
    return (static_cast<const Allocation *>(ptr) - 1)->size;
}

void Free(JSMallocState *state, void *ptr) {
    if (!ptr) return;
    state->malloc_size -= sizeof(Allocation) + AllocationSize(ptr);
    --state->malloc_count;
    std::free(static_cast<Allocation *>(ptr) - 1);
}

void *Reallocate(JSMallocState *state, void *ptr, size_t size) {
    if (size == 0) {
        Free(state, ptr);
        return nullptr;
    }
    const size_t used = state->malloc_size - (ptr ? sizeof(Allocation) + AllocationSize(ptr) : 0);
    const size_t limit = state->malloc_limit < kHeapLimit ? state->malloc_limit : kHeapLimit;
    if (used <= limit && limit - used >= sizeof(Allocation) &&
        size <= limit - used - sizeof(Allocation)) {
        const bool newAllocation = ptr == nullptr;
        auto *old = ptr ? static_cast<Allocation *>(ptr) - 1 : nullptr;
        auto *allocation = static_cast<Allocation *>(std::realloc(old, sizeof(Allocation) + size));
        if (allocation) {
            allocation->size = size;
            state->malloc_size = used + sizeof(Allocation) + size;
            if (newAllocation) ++state->malloc_count;
            return allocation + 1;
        }
    }
    auto &budgetError = static_cast<Engine *>(state->opaque)->budgetError;
    if (!budgetError) budgetError = "QuickJS heap limit exceeded";
    return nullptr;
}

void *Allocate(JSMallocState *state, size_t size) {
    return Reallocate(state, nullptr, size);
}

constexpr JSMallocFunctions kAllocator{Allocate, Free, Reallocate, AllocationSize};

std::mutex enginesMutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> engines;
jlong nextHandle = 1;

std::shared_ptr<Engine> FindEngine(jlong handle) {
    std::lock_guard lock(enginesMutex);
    auto item = engines.find(handle);
    return item == engines.end() ? nullptr : item->second;
}

void Throw(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception) env->ThrowNew(exception, message);
}

void ClearJsException(JSContext *ctx) {
    JS_FreeValue(ctx, JS_GetException(ctx));
}

bool DrainJobs(Engine &engine) {
    while (JS_IsJobPending(engine.runtime)) {
        if (engine.BudgetExceeded()) return false;
        JSContext *jobContext = nullptr;
        if (JS_ExecutePendingJob(engine.runtime, &jobContext) < 0) {
            ClearJsException(jobContext ? jobContext : engine.context);
            return false;
        }
    }
    return !engine.BudgetExceeded();
}

struct Completion {
    JSContext *ctx;
    JSValue value;

    ~Completion() { JS_FreeValue(ctx, value); }
};

jbyteArray Evaluate(JNIEnv *env, Engine &engine, const std::string &source) {
    JSContext *ctx = engine.context;
    engine.deadline = Clock::now() + kEvaluationTimeout;
    JS_UpdateStackTop(engine.runtime);

    Completion result{ctx, JS_Eval(ctx, source.c_str(), source.size(), "<lx>", JS_EVAL_TYPE_GLOBAL)};
    const bool evalFailed = JS_IsException(result.value);
    if (evalFailed) ClearJsException(ctx);
    if (!DrainJobs(engine) || evalFailed) {
        Throw(env, engine.budgetError ? engine.budgetError : "QuickJS evaluation failed");
        return nullptr;
    }

    const auto promiseState = JS_PromiseState(ctx, result.value);
    if (promiseState == JS_PROMISE_PENDING || promiseState == JS_PROMISE_REJECTED) {
        Throw(env, promiseState == JS_PROMISE_PENDING
                       ? "QuickJS promise did not settle" : "QuickJS promise rejected");
        return nullptr;
    }
    if (promiseState == JS_PROMISE_FULFILLED) {
        JSValue fulfilled = JS_PromiseResult(ctx, result.value);
        JS_FreeValue(ctx, result.value);
        result.value = fulfilled;
    }

    size_t length = 0;
    const char *utf8 = JS_ToCStringLen(ctx, &length, result.value);
    if (!utf8) ClearJsException(ctx);
    // Completion-value coercion can itself execute JS and queue microtasks.
    jbyteArray bytes = nullptr;
    if (!DrainJobs(engine) || !utf8) {
        Throw(env, engine.budgetError ? engine.budgetError : "QuickJS evaluation failed");
    } else if (length > kTransferLimit) {
        Throw(env, "QuickJS result limit exceeded");
    } else {
        bytes = env->NewByteArray(static_cast<jsize>(length));
        if (bytes && length != 0) {
            env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(length),
                                    reinterpret_cast<const jbyte *>(utf8));
        }
    }
    if (utf8) JS_FreeCString(ctx, utf8);
    return env->ExceptionCheck() ? nullptr : bytes;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_currencortex_music_data_lx_LxQuickJsNative_create(JNIEnv *env, jobject) {
    try {
        auto engine = std::make_shared<Engine>();
        engine->runtime = JS_NewRuntime2(&kAllocator, engine.get());
        if (engine->runtime) {
            JS_SetMemoryLimit(engine->runtime, kHeapLimit);
            JS_SetMaxStackSize(engine->runtime, kStackLimit);
            JS_SetCanBlock(engine->runtime, false);
            // The standard ECMAScript context exposes no QuickJS libc helpers or module loader.
            engine->context = JS_NewContext(engine->runtime);
            JS_SetInterruptHandler(engine->runtime, [](JSRuntime *, void *opaque) -> int {
                return static_cast<Engine *>(opaque)->BudgetExceeded();
            }, engine.get());
        }
        if (engine->context && !engine->budgetError) {
            std::lock_guard lock(enginesMutex);
            if (nextHandle < std::numeric_limits<jlong>::max()) {
                const jlong handle = nextHandle++;
                engines.emplace(handle, std::move(engine));
                return handle;
            }
        }
        Throw(env, engine->budgetError ? engine->budgetError : "QuickJS initialization failed");
    } catch (const std::bad_alloc &) {
        Throw(env, "QuickJS heap allocation failed");
    }
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_currencortex_music_data_lx_LxQuickJsNative_evaluate(
        JNIEnv *env, jobject, jlong handle, jbyteArray input) {
    auto engine = FindEngine(handle);
    if (!engine || !input) {
        Throw(env, "QuickJS invalid evaluation arguments");
        return nullptr;
    }
    const jsize length = env->GetArrayLength(input);
    if (static_cast<size_t>(length) > kTransferLimit) {
        Throw(env, "QuickJS input limit exceeded");
        return nullptr;
    }
    std::lock_guard lock(engine->mutex);
    // Interrupted executions may leave queued jobs in the engine.
    if (engine->budgetError) {
        Throw(env, engine->budgetError);
        return nullptr;
    }
    try {
        std::string source(static_cast<size_t>(length), '\0');
        if (length != 0) {
            env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte *>(source.data()));
            if (env->ExceptionCheck()) return nullptr;
        }
        return Evaluate(env, *engine, source);
    } catch (const std::bad_alloc &) {
        Throw(env, "QuickJS heap allocation failed");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_currencortex_music_data_lx_LxQuickJsNative_destroy(JNIEnv *, jobject, jlong handle) {
    std::shared_ptr<Engine> engine;
    {
        std::lock_guard lock(enginesMutex);
        auto item = engines.find(handle);
        if (item != engines.end()) {
            engine = std::move(item->second);
            engines.erase(item);
        }
    }
}
