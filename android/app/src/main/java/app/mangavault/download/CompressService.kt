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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Runs a file compression in the foreground so it survives leaving the app; progress and cancel in the notification. */
class CompressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val req = CompressState.pending
        if (req == null || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        CompressState.pending = null
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, progress(req.name, 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        job = scope.launch {
            val watcher = launch {
                var last = 0L
                CompressState.job.collect { s ->
                    if (s == null || s.status != JobStatus.RUNNING) return@collect
                    val now = System.currentTimeMillis()
                    if (now - last > 800) { last = now; notify(NOTIF_ID, progress(s.name, (s.fraction * 100).toInt())) }
                }
            }
            try {
                Compressor.run(applicationContext, req)
            } catch (_: CancellationException) {
            } catch (_: Throwable) {
            } finally {
                watcher.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                CompressState.job.value?.let { notify(DONE_ID, done(it)) }
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

    private fun openApp() = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun progress(title: String, percent: Int): Notification {
        val cancel = PendingIntent.getService(this, 3, Intent(this, CompressService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }

    private fun done(s: CompressJob): Notification {
        val text = when {
            s.status == JobStatus.DONE && !s.noGain -> "${s.inBytes / 1048576} MB → ${s.outBytes / 1048576} MB"
            s.status == JobStatus.DONE -> getString(R.string.notif_done)
            s.status == JobStatus.CANCELLED -> getString(R.string.notif_cancelled)
            else -> getString(R.string.notif_failed)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (s.status == JobStatus.DONE) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(s.name)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    companion object {
        const val ACTION_CANCEL = "app.mangavault.COMPRESS_CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 21
        private const val DONE_ID = 22
    }
}
