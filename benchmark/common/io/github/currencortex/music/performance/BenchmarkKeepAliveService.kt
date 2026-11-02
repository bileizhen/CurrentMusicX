package io.github.currencortex.music.performance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test APK only: keep the instrumentation UID foreground while its target is launched/killed. */
class BenchmarkKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            "CurrentMusic performance tests", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        })
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("CurrentMusic performance test running")
            .setContentText("Keeping only the instrumentation process active")
            .setOngoing(true).setOnlyAlertOnce(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started?.countDown()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "cm-benchmark-harness"
        private const val NOTIFICATION_ID = 54820
        @Volatile private var started: CountDownLatch? = null

        fun start(context: Context) {
            val latch = CountDownLatch(1)
            started = latch
            context.startForegroundService(Intent(context, BenchmarkKeepAliveService::class.java))
            check(latch.await(10, TimeUnit.SECONDS)) { "Instrumentation foreground service did not start" }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BenchmarkKeepAliveService::class.java))
            started = null
        }
    }
}
