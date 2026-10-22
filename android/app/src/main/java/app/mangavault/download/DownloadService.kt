package app.mangavault.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mangavault.MainActivity
import app.mangavault.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the download alive in the background with an ongoing notification (progress + cancel). */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val req = JobHolder.pending
        if (req == null || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        JobHolder.pending = null
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(req.series.title, 0, true),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        job = scope.launch {
            val watcher = launch {
                var lastTime = 0L
                var lastPct = -1
                DownloadState.job.collect { s ->
                    if (s == null || s.status != JobStatus.RUNNING) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastTime > 700 && s.percent != lastPct) {
                        lastTime = now; lastPct = s.percent
                        notify(NOTIF_ID, notification(s.title, s.percent, true))
                    }
                }
            }
            try {
                Downloader.run(applicationContext, req)
            } catch (_: CancellationException) {
            } finally {
                watcher.cancel()
                val s = DownloadState.job.value
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (s != null) notify(DONE_ID, finalNotification(s))
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(id: Int, n: Notification) {
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) } catch (_: Exception) { }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notification(title: String, percent: Int, ongoing: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }

    private fun finalNotification(s: JobState): Notification {
        val text = when (s.status) {
            JobStatus.DONE -> getString(R.string.notif_done)
            JobStatus.CANCELLED -> getString(R.string.notif_cancelled)
            else -> getString(R.string.notif_failed)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (s.status == JobStatus.DONE) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(s.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    companion object {
        const val ACTION_CANCEL = "app.mangavault.CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 11
        private const val DONE_ID = 12
    }
}
