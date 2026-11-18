package io.github.currencortex.music.data.lx

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

internal object LxQuickJsNative {
    external fun create(): Long
    external fun evaluate(handle: Long, utf8: ByteArray): ByteArray
    external fun destroy(handle: Long)

    init {
        System.loadLibrary("currentmusic_quickjs")
    }
}

internal class LxQuickJsSandbox private constructor(private val app: Context) : Closeable {
    private val stateLock = Any()
    private val evaluations = Mutex()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ready = CompletableDeferred<IBinder>()
    private var pending: CompletableDeferred<*>? = null
    private var bindAttempted = false
    private var legacyLease = false
    @Volatile private var closed = false
    private var root: IBinder? = null
    private lateinit var session: IBinder
    private val death = IBinder.DeathRecipient { close() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(stateLock) {
                if (closed || root != null) return
                try {
                    service.linkToDeath(death, 0)
                    root = service
                    ready.complete(service)
                } catch (_: Exception) {
                    close()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = close()
        override fun onBindingDied(name: ComponentName) = close()
        override fun onNullBinding(name: ComponentName) = close()
    }

    private fun bind() = synchronized(stateLock) {
        val intent = Intent(app, LxQuickJsService::class.java)
        if (Build.VERSION.SDK_INT < 29) {
            // Legacy binding uses one service instance for the component.
            check(legacyBinding.tryLock()) { "QuickJS service is already bound" }
            legacyLease = true
        }
        bindAttempted = true
        val accepted = if (Build.VERSION.SDK_INT >= 29) {
            // The framework rejects the hyphens in the standard UUID representation.
            val instanceName = UUID.randomUUID().toString().replace("-", "")
            app.bindIsolatedService(intent, Context.BIND_AUTO_CREATE, instanceName, app.mainExecutor, connection)
        } else {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        check(accepted) { "QuickJS could not bind" }
    }

    suspend fun evaluate(code: String): String = evaluations.withLock {
        callOnIo {
            check(code.length <= MAX_MESSAGE_BYTES)
            val input = code.toByteArray(Charsets.UTF_8)
            check(input.size <= MAX_MESSAGE_BYTES)
            session.call(BEGIN, { writeInt(input.size) }) { }
            var offset = 0
            while (offset < input.size) {
                val count = minOf(CHUNK_BYTES, input.size - offset)
                session.call(UPLOAD, { writeByteArray(input, offset, count) }) { }
                offset += count
            }

            val length = session.call(EVALUATE) { readInt().also { check(it in 0..MAX_MESSAGE_BYTES) } }
            val output = ByteArray(length)
            offset = 0
            while (offset < length) {
                val chunk = ByteArray(minOf(CHUNK_BYTES, length - offset))
                session.call(DOWNLOAD) { readByteArray(chunk) }
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
            output.toString(Charsets.UTF_8)
        }
    }

    // Binder's synchronous transact cannot be interrupted, so await a separate result. Cancelling
    // the waiter unbinds immediately and lets the service terminate a stuck native evaluation.
    private suspend fun <T> callOnIo(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = CompletableDeferred<T>()
        synchronized(stateLock) {
            if (closed) throw unavailable()
            check(pending == null)
            pending = result
        }
        val work = io.launch {
            try {
                result.complete(block())
            } catch (_: Exception) {
                result.completeExceptionally(unavailable())
            } catch (_: LinkageError) {
                result.completeExceptionally(unavailable())
            }
        }
        var completed = false
        try {
            val value = result.await()
            completed = true
            return value
        } finally {
            if (!completed) close()
            work.cancel()
            synchronized(stateLock) { pending = null }
        }
    }

    override fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true
            io.cancel()
            root?.let { runCatching { it.unlinkToDeath(death, 0) } }
            root = null
            if (bindAttempted) runCatching { app.unbindService(connection) }
            if (legacyLease) legacyBinding.unlock()
            ready.completeExceptionally(unavailable())
            pending?.completeExceptionally(unavailable())
        }
    }

    private fun <T> IBinder.call(
        operation: Int,
        write: Parcel.() -> Unit = {},
        read: Parcel.() -> T,
    ): T {
        if (closed) throw unavailable()
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.write()
            check(transact(operation, request, response, 0))
            check(response.dataSize() in Int.SIZE_BYTES..MAX_PARCEL_BYTES)
            check(response.readInt() == OK)
            val value = response.read()
            check(response.dataAvail() == 0)
            return value
        } finally {
            response.recycle()
            request.recycle()
        }
    }

    companion object {
        private val legacyBinding = Mutex()

        suspend fun connect(context: Context): LxQuickJsSandbox {
            currentCoroutineContext().ensureActive()
            val sandbox = LxQuickJsSandbox(context.applicationContext)
            var connected = false
            try {
                try {
                    sandbox.bind()
                } catch (failure: Exception) {
                    Log.w("LxQuickJsSandbox", "QuickJS bind failed (${failure.javaClass.simpleName})")
                    val debuggable = sandbox.app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                    throw unavailable(failure.takeIf { debuggable })
                }
                val root = sandbox.ready.await()
                sandbox.session = sandbox.callOnIo {
                    with(sandbox) { root.call(OPEN) { checkNotNull(readStrongBinder()) } }
                }
                if (sandbox.closed) throw unavailable()
                connected = true
                return sandbox
            } finally {
                if (!connected) sandbox.close()
            }
        }
    }
}

class LxQuickJsService : Service() {
    // QuickJS uses the native thread's stack limit. Run creation, evaluation and destruction
    // on one thread even when different Binder threads handle the individual transactions.
    private val worker = Executors.newSingleThreadExecutor()
    private val transaction = ReentrantLock()
    @Volatile private var stopping = false
    private var active: Session? = null

    override fun onBind(intent: Intent): IBinder? {
        if (Process.myUid() == applicationInfo.uid) return null
        val isolated = if (Build.VERSION.SDK_INT >= 28) Process.isIsolated()
            else !Process.isApplicationUid(Process.myUid())
        return if (isolated) endpoint else null
    }

    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != OPEN || reply == null) return super.onTransact(code, data, reply, flags)
            return dispatch(data, reply) {
                check(data.dataAvail() == 0)
                val previous = active
                active = null
                val handle = worker.submit(Callable {
                    previous?.destroy()
                    LxQuickJsNative.create()
                }).get()
                check(handle != 0L)
                val session = Session(handle)
                active = session
                reply.writeInt(OK)
                reply.writeStrongBinder(session)
            }
        }
    }

    private inner class Session(private val handle: Long) : Binder() {
        private var input: ByteArray? = null
        private var inputOffset = 0
        private var output: ByteArray? = null
        private var outputOffset = 0

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in BEGIN..DOWNLOAD || reply == null) return super.onTransact(code, data, reply, flags)
            return dispatch(data, reply) {
                check(active === this)
                when (code) {
                    BEGIN -> {
                        check(input == null && output == null && data.dataAvail() == Int.SIZE_BYTES)
                        val size = data.readInt()
                        check(size in 0..MAX_MESSAGE_BYTES)
                        input = ByteArray(size)
                        inputOffset = 0
                        reply.writeInt(OK)
                    }
                    UPLOAD -> {
                        val buffer = checkNotNull(input)
                        val count = minOf(CHUNK_BYTES, buffer.size - inputOffset)
                        check(count > 0)
                        val chunk = ByteArray(count)
                        data.readByteArray(chunk)
                        check(data.dataAvail() == 0)
                        chunk.copyInto(buffer, inputOffset)
                        inputOffset += count
                        reply.writeInt(OK)
                    }
                    EVALUATE -> {
                        val buffer = checkNotNull(input)
                        check(inputOffset == buffer.size && data.dataAvail() == 0)
                        input = null
                        val result = worker.submit(Callable {
                            LxQuickJsNative.evaluate(handle, buffer)
                        }).get()
                        check(result.size <= MAX_MESSAGE_BYTES)
                        output = result.takeUnless { it.isEmpty() }
                        outputOffset = 0
                        reply.writeInt(OK)
                        reply.writeInt(result.size)
                    }
                    DOWNLOAD -> {
                        val buffer = checkNotNull(output)
                        check(data.dataAvail() == 0)
                        val count = minOf(CHUNK_BYTES, buffer.size - outputOffset)
                        reply.writeInt(OK)
                        reply.writeByteArray(buffer, outputOffset, count)
                        outputOffset += count
                        if (outputOffset == buffer.size) output = null
                    }
                }
            }
        }

        fun destroy() {
            input = null
            output = null
            LxQuickJsNative.destroy(handle)
        }
    }

    private fun dispatch(data: Parcel, reply: Parcel, block: () -> Unit): Boolean {
        if (stopping || data.dataSize() > MAX_PARCEL_BYTES || !transaction.tryLock()) {
            reply.writeInt(ERROR)
            return true
        }
        try {
            check(!stopping)
            data.enforceInterface(DESCRIPTOR)
            block()
        } catch (_: Exception) {
            // Return only a status code, including for JNI failures and malformed messages.
            reply.setDataPosition(0)
            reply.setDataSize(0)
            reply.writeInt(ERROR)
        } finally {
            transaction.unlock()
        }
        return true
    }

    override fun onUnbind(intent: Intent): Boolean {
        terminate()
        return false
    }

    override fun onDestroy() {
        terminate()
        super.onDestroy()
    }

    private fun terminate() {
        if (stopping) return
        stopping = true
        if (!transaction.tryLock()) {
            // Never wait on QuickJS from the service's main thread during unbind.
            Process.killProcess(Process.myPid())
            return
        }
        try {
            worker.submit {
                val session = active
                active = null
                session?.destroy()
            }
            worker.shutdown()
        } finally {
            transaction.unlock()
        }
    }
}

private const val DESCRIPTOR = "io.github.currencortex.music.lx.QuickJsSandbox"
private const val OPEN = IBinder.FIRST_CALL_TRANSACTION
private const val BEGIN = OPEN + 1
private const val UPLOAD = OPEN + 2
private const val EVALUATE = OPEN + 3
private const val DOWNLOAD = OPEN + 4
private const val OK = 0
private const val ERROR = 1
private const val CHUNK_BYTES = 64 * 1024
private const val MAX_PARCEL_BYTES = CHUNK_BYTES + 1024
private const val MAX_MESSAGE_BYTES = 8 * 1024 * 1024

private fun unavailable(cause: Exception? = null) = IllegalStateException("QuickJS sandbox unavailable", cause)
