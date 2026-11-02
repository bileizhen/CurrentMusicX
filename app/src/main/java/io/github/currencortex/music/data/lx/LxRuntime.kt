package io.github.currencortex.music.data.lx

import android.content.Context
import android.os.SystemClock
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Modern JS, without a WebView, Java bridge, filesystem, account storage or application process heap. */
class LxRuntime(context: Context, private val http: LxHttpClient = LxHttpClient()) : java.io.Closeable {
    private val app = context.applicationContext
    private val lock = Mutex()
    @Volatile private var sandbox: JavaScriptSandbox? = null
    @Volatile private var isolate: JavaScriptIsolate? = null
    private var activeId = ""
    private var initialized: JsonObject? = null
    // Test diagnostics only. Never connected to UI or persistent logs: scripts can include private keys in errors.
    internal var failureObserver: ((String) -> Unit)? = null
    private val crypto by lazy { app.assets.open("lx/crypto-js.js").bufferedReader().use { it.readText() } }
    private val host by lazy { app.assets.open("lx/host.js").bufferedReader().use { it.readText() } }
    fun supported() = JavaScriptSandbox.isSupported()
    suspend fun inspect(info: LxSourceInfo, script: String): List<LxCapability> = lock.withLock {
        try { ensure(info, script); lxCapabilities(requireNotNull(initialized)) }
        catch (_: TimeoutCancellationException) { close(); currentCoroutineContext().ensureActive(); throw LxSourceException("落雪音源初始化超时，请重试或更换音源") }
        catch (cancelled: CancellationException) { close(); throw cancelled }
        catch (failure: Exception) { close(); throw readable(failure) }
    }
    suspend fun resolve(info: LxSourceInfo, script: String, data: JsonObject): String = lock.withLock {
        try {
            ensure(info, script)
            evaluate("__cm_lx.start($data); 'ok'")
            val answer = pump(forInitialization = false)
            if (answer["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                // Test-only diagnostics, never include arbitrary script errors in UI or logs.
                runCatching { failureObserver?.invoke(answer["message"]?.jsonPrimitive?.contentOrNull.orEmpty().take(256)) }
                throw LxSourceException("音源未能解析这首歌曲，请重试或切换音源")
            }
            val value = answer["value"] as? JsonPrimitive
            if (value?.isString != true || value.content.length > 8192) throw LxSourceException("音源没有返回有效的播放链接")
            http.url(value.content).toString()
        } catch (_: TimeoutCancellationException) { close(); currentCoroutineContext().ensureActive(); throw LxSourceException("落雪音源解析超时，请重试或更换音源") }
        catch (cancelled: CancellationException) { close(); throw cancelled }
        catch (failure: Exception) { close(); throw readable(failure) }
    }
    private suspend fun ensure(info: LxSourceInfo, script: String) {
        if (activeId == info.id && isolate != null && initialized != null) return
        close()
        if (!supported()) throw LxSourceException("当前系统不支持落雪音源运行环境，请更新 Android System WebView 后重试")
        val engine = withTimeout(10000) { JavaScriptSandbox.createConnectedInstanceAsync(app).await() }
        sandbox = engine
        if (!engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE) ||
            !engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION) ||
            !engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN)) {
            throw LxSourceException("当前 WebView 缺少脚本隔离能力，请更新 Android System WebView")
        }
        if (!engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT) && script.length > 100000)
            throw LxSourceException("当前 WebView 不支持较大的音源脚本，请更新后重试")
        val parameters = IsolateStartupParameters().apply { setMaxHeapSizeBytes(64L * 1024 * 1024) }
        isolate = engine.createIsolate(parameters)
        evaluate(crypto)
        evaluate(host)
        val metadata = buildJsonObject {
            put("name", info.name); put("description", info.description); put("author", info.author)
            put("version", info.version); put("homepage", info.homepage); put("rawScript", script)
        }
        val seed = JsonPrimitive(Base64.getEncoder().encodeToString(ByteArray(65536).also { SecureRandom().nextBytes(it) }))
        evaluate("__cm_lx.setup($metadata,$seed,${SystemClock.elapsedRealtime()}); 'ok'")
        // Function keeps browser-script semantics independent of the engine's evaluation scope.
        // Some existing sources assign implicit globals; retain their exact text and own strictness.
        evaluate("try { (new Function(${JsonPrimitive(script)})).call(globalThis); } catch(e) { __cm_lx.fail(e); } 'ok'")
        initialized = pump(forInitialization = true)
        lxCapabilities(requireNotNull(initialized))
        activeId = info.id
    }
    private suspend fun evaluate(code: String): String = withTimeout(2500) {
        requireNotNull(isolate).evaluateJavaScriptAsync(code).await()
    }
    private suspend fun pump(forInitialization: Boolean): JsonObject = withTimeout(25000) {
        coroutineScope {
            val pending = mutableMapOf<Int, Deferred<JsonObject>>()
            var count = 0
            try {
                while (true) {
                    val state = Json.parseToJsonElement(evaluate("Promise.resolve().then(()=>__cm_lx.take(${SystemClock.elapsedRealtime()}))")).jsonObject
                    if (state["failure"] !is JsonNull && state["failure"] != null) {
                        failureObserver?.invoke(state["failure"]!!.jsonPrimitive.content.take(256))
                        throw LxSourceException(if (forInitialization) "音源脚本初始化失败，可能不兼容或已失效" else "音源脚本执行失败，请重试或切换音源")
                    }
                    val done = state[if (forInitialization) "initialized" else "answer"] as? JsonObject
                    // A final musicUrl result can stop speculative requests, but `inited`
                    // only declares capabilities. Sources may already be fetching config
                    // which their request handler awaits: do not discard those callbacks.
                    if (!forInitialization && done != null) {
                        evaluate("__cm_lx.clearPending(); 'ok'")
                        return@coroutineScope done
                    }
                    (state["cancelled"] as? JsonArray).orEmpty().forEach { pending.remove(it.jsonPrimitive.int)?.cancel() }
                    for (item in (state["requests"] as? JsonArray).orEmpty()) {
                        if (++count > 64 || pending.size >= 8) throw LxSourceException("音源发起的请求过多")
                        val request = item.jsonObject
                        val id = request["id"]!!.jsonPrimitive.int
                        require(id > 0 && id !in pending)
                        pending[id] = async(Dispatchers.IO) {
                            try {
                                val op = request["op"]!!.jsonPrimitive.content
                                val response: JsonElement = when (op) {
                                    "http" -> http.execute(request)
                                    "inflate", "deflate" -> JsonPrimitive(compress(op, request["data"]!!.jsonPrimitive.content))
                                    else -> throw LxSourceException("脚本调用了不支持的接口")
                                }
                                buildJsonObject { put("id", id); put("op", op); put("value", response) }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { buildJsonObject { put("id", id); put("error", "Source request failed") } }
                        }
                    }
                    val complete = pending.filterValues { it.isCompleted }.keys.toList()
                    if (complete.isNotEmpty()) {
                        val replies = JsonArray(complete.map { pending.remove(it)!!.await() })
                        evaluate("__cm_lx.reply($replies); 'ok'")
                    } else if (forInitialization && done != null && pending.isEmpty()) {
                        return@coroutineScope done
                    } else delay(15)
                }
                @Suppress("UNREACHABLE_CODE") throw LxSourceException("音源没有完成请求")
            } finally { pending.values.forEach { it.cancel() } }
        }
    }
    private fun compress(op: String, value: String): String {
        require(value.length <= LX_MAX_RESPONSE_BYTES * 2)
        val input = Base64.getDecoder().decode(value)
        require(input.size <= LX_MAX_RESPONSE_BYTES)
        val output = if (op == "inflate") InflaterInputStream(input.inputStream()).use { lxReadBounded(it, LX_MAX_RESPONSE_BYTES) }
            else java.io.ByteArrayOutputStream().also { stream -> DeflaterOutputStream(stream).use { it.write(input) } }.toByteArray()
        require(output.size <= LX_MAX_RESPONSE_BYTES)
        return Base64.getEncoder().encodeToString(output)
    }
    private fun readable(failure: Exception): LxSourceException = if (failure is LxSourceException) failure
        else LxSourceException("落雪音源执行失败或超出运行限制，请重试或更换音源")
    override fun close() {
        val engine = sandbox; val instance = isolate
        sandbox = null; isolate = null
        activeId = ""; initialized = null
        // Unbinding the service terminates the whole process before local isolate cleanup.
        // This also covers an evaluation that never returns to the JavaScript event loop.
        engine?.close()
        instance?.close()
    }
}
