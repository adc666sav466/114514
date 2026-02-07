package com.example.randomtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

 main
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class RandomTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentMode: TimerMode = TimerMode.STUDY
    private var level: DifficultyLevel = DifficultyLevel.EASY
    private var nextSwitchAtElapsed: Long = -1L

    private val switchRunnable = Runnable {
        currentMode = if (currentMode == TimerMode.STUDY) TimerMode.REST else TimerMode.STUDY
        notifyModeStart(currentMode)
        scheduleNextSwitch()
        broadcastStatus(currentMode)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                level = DifficultyLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: DifficultyLevel.EASY.name)
                if (!canPostNotifications()) {
                    handler.post {
                        Toast.makeText(
                            this,
                            R.string.notification_permission_denied,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startForeground(NOTIFICATION_ID, buildRunningNotification())
                    handler.removeCallbacks(switchRunnable)
                    currentMode = TimerMode.STUDY
                    notifyModeStart(currentMode)
               
                    broadcastStatus(currentMode)
                    scheduleNextSwitch()
main
                } catch (exception: SecurityException) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_STOP -> {
                handler.removeCallbacks(switchRunnable)
                nextSwitchAtElapsed = -1L
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(switchRunnable)
        super.onDestroy()
    }

    private fun scheduleNextSwitch() {
        val durationMinutes = when (currentMode) {
            TimerMode.STUDY -> randomMinutesFor(level).study
            TimerMode.REST -> randomMinutesFor(level).rest
        }
        val delayMillis = durationMinutes * 60_000L
        nextSwitchAtElapsed = SystemClock.elapsedRealtime() + delayMillis
        if (delayMillis == 0L) {
            handler.post(switchRunnable)
        } else {
            handler.postDelayed(switchRunnable, delayMillis)
        }
    }

    private fun randomMinutesFor(level: DifficultyLevel): DurationPair {
        val config = when (level) {
            DifficultyLevel.EASY -> LevelConfig(20, 40, 0, 10)
            DifficultyLevel.MEDIUM -> LevelConfig(35, 60, 0, 15)
            DifficultyLevel.HARD -> LevelConfig(50, 90, 0, 20)
        }
        val study = Random.nextInt(config.studyMin, config.studyMax + 1)
        val rest = Random.nextInt(config.restMin, config.restMax + 1)
        return DurationPair(study, rest)
    }

    private fun notifyModeStart(mode: TimerMode) {
        createNotificationChannel()
        val title = when (mode) {
            TimerMode.STUDY -> getString(R.string.notification_study)
            TimerMode.REST -> getString(R.string.notification_rest)
        }
        if (canPostNotifications()) {

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(getString(R.string.notification_running))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
main
                .build()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(MODE_NOTIFICATION_ID, notification)
        }
    }

    private fun broadcastStatus(mode: TimerMode) {
        val statusText = when (mode) {
            TimerMode.STUDY -> getString(R.string.status_study)
            TimerMode.REST -> getString(R.string.status_rest)
        }
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, statusText)
            putExtra(EXTRA_NEXT_SWITCH_AT, nextSwitchAtElapsed)
        }
        sendBroadcast(intent)
    }

    private fun buildRunningNotification(): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_running))
            .setContentText(getString(R.string.app_name))
            .setOngoing(true)
            .build()
    }

    private fun canPostNotifications(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val runningChannel = NotificationChannel(
                RUNNING_CHANNEL_ID,
                getString(R.string.notification_running),
                NotificationManager.IMPORTANCE_LOW
            )
            val modeChannel = NotificationChannel(
                MODE_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(runningChannel)
            manager.createNotificationChannel(modeChannel)
        }
    }

    private data class LevelConfig(
        val studyMin: Int,
        val studyMax: Int,
        val restMin: Int,
        val restMax: Int
    )

    private data class DurationPair(
        val study: Int,
        val rest: Int
    )

    private enum class TimerMode {
        STUDY,
        REST
    }

    companion object {
        const val ACTION_START = "com.example.randomtimer.action.START"
        const val ACTION_STOP = "com.example.randomtimer.action.STOP"
        const val ACTION_STATUS = "com.example.randomtimer.action.STATUS"
        const val EXTRA_LEVEL = "com.example.randomtimer.extra.LEVEL"
        const val EXTRA_STATUS = "com.example.randomtimer.extra.STATUS"
        const val EXTRA_NEXT_SWITCH_AT = "com.example.randomtimer.extra.NEXT_SWITCH_AT"

        private const val RUNNING_CHANNEL_ID = "random_timer_running_channel"
        private const val MODE_CHANNEL_ID = "random_timer_mode_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MODE_NOTIFICATION_ID = 1002
    }
}
