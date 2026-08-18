package nl.mikesmits.p2000

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.mikesmits.p2000.data.P2000Data
import nl.mikesmits.p2000.ui.MainActivity

/**
 * Foreground-service die de P2000-feed op de achtergrond blijft ophalen zodat
 * de historie ook doorloopt als de app niet open staat.
 */
class PollService : Service() {

    companion object {
        @Volatile
        var running = false
            private set
        private const val CHANNEL_ID = "polling"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 20_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        P2000Data.init(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        running = true
        scope.launch {
            val repository = P2000Data.repository
            repository.ensureLoaded()
            while (isActive) {
                runCatching {
                    repository.refresh()
                    repository.geocodeMissing(repository.current())
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_polling),
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_type_overig)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.polling_notification))
            .setOngoing(true)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
