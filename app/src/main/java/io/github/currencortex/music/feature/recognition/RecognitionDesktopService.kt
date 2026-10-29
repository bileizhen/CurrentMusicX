package io.github.currencortex.music.feature.recognition

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.currencortex.music.CurrentMusicApplication
import io.github.currencortex.music.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Started only by a visible, permission-granted activity. Never restarts a microphone in the background. */
class RecognitionDesktopService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: RecognitionController? = null
    private var island: AutoCloseable? = null
    private var watcher: Job? = null
    private var deadline: Job? = null
    private var registered = false
    private val appOps by lazy { getSystemService(AppOpsManager::class.java) }
    private val permission = AppOpsManager.OnOpChangedListener { _, pkg ->
        if (pkg == packageName) scope.launch {
            if (!Settings.canDrawOverlays(this@RecognitionDesktopService) ||
                ContextCompat.checkSelfPermission(this@RecognitionDesktopService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) stopSelf()
        }
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { stopSelf() }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopSelf(); return START_NOT_STICKY }
            FINISH -> { controller?.finishRecording(); return START_NOT_STICKY }
            START -> if (controller == null) startAttempt() else if (controller?.state?.value?.busy == false) {
                controller?.start()
                deadline?.cancel(); deadline = scope.launch { delay(45_000); stopSelf() }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun startAttempt() {
        try {
            check(Settings.canDrawOverlays(this)) { "请先开启悬浮窗权限" }
            check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "请先开启麦克风权限" }
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "桌面听歌识曲", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            ServiceCompat.startForeground(this, NOTICE, notification(RecognitionState(RecognitionPhase.RECORDING)),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            val options = (application as CurrentMusicApplication).container.desktopLyrics
            val encoder = NeteaseFingerprint(this)
            val repository = NeteaseRecognitionRepository()
            val next = RecognitionController(scope, MicrophoneRecorder(), encoder::encode, repository::recognize)
            controller = next
            ContextCompat.registerReceiver(this, screen, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permission)
            appOps.startWatchingMode(AppOpsManager.OPSTR_RECORD_AUDIO, packageName, permission)
            next.start()
            RecognitionDesktopSession.publish(next.state.value)
            RecognitionDesktopSession.attach()
            island = io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHosts.acquire(this, (application as CurrentMusicApplication).container)
            watcher = scope.launch {
                next.state.map { it.copy(level = 0f) }.distinctUntilChanged().collectLatest { state ->
                    RecognitionDesktopSession.publish(state)
                    runCatching { notifications.notify(NOTICE, notification(state)) }
                    if (!state.busy && state.phase != RecognitionPhase.IDLE) {
                        if (state.phase == RecognitionPhase.MATCHED && state.songs.isNotEmpty())
                            withContext(Dispatchers.IO) { RecognitionHistory(this@RecognitionDesktopService).add(state.songs.first()) }
                        delay(if (state.phase == RecognitionPhase.MATCHED) 20_000 else 8_000)
                        stopSelf()
                    }
                }
            }
            // Includes local encoding and HTTP timeouts; no abandoned foreground service.
            deadline = scope.launch { delay(45_000); stopSelf() }
        } catch (failure: Exception) {
            RecognitionDesktopSession.publish(RecognitionState(RecognitionPhase.ERROR, message = "无法开启桌面识曲，请检查麦克风和悬浮窗权限"))
            stopSelf()
        }
    }
    private fun notification(state: RecognitionState): Notification {
        val open = PendingIntent.getActivity(this, 921, openIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 922, Intent(this, RecognitionDesktopService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(recognitionIslandTitle(state)).setContentText(recognitionIslandSubtitle(state))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "停止识曲", stop).build()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        controller?.cancel()
        watcher?.cancel(); deadline?.cancel()
        if (registered) { unregisterReceiver(screen); appOps.stopWatchingMode(permission) }
        if (RecognitionDesktopSession.state.value.busy) RecognitionDesktopSession.publish(RecognitionState())
        RecognitionDesktopSession.detach()
        island?.close(); island = null
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private const val START = "recognition.desktop.start"
        private const val STOP = "recognition.desktop.stop"
        private const val FINISH = "recognition.desktop.finish"
        const val OPEN = "recognition.desktop.open"
        private const val CHANNEL = "music_recognition"
        private const val NOTICE = 920
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, RecognitionDesktopService::class.java).setAction(START))
        fun stop(context: Context) = context.stopService(Intent(context, RecognitionDesktopService::class.java))
        fun finish(context: Context) { if (RecognitionDesktopSession.attached.value) context.startService(Intent(context, RecognitionDesktopService::class.java).setAction(FINISH)) }
        internal fun islandAction(context: Context) {
            when (RecognitionDesktopSession.state.value.phase) {
                RecognitionPhase.RECORDING -> finish(context)
                RecognitionPhase.RECOGNIZING -> stop(context)
                else -> { context.startActivity(openIntent(context)); stop(context) }
            }
        }
        fun openIntent(context: Context) = Intent(context, MainActivity::class.java).putExtra(OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
