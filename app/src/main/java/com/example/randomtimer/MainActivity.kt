package com.example.randomtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var timeRemainingText: TextView
    private lateinit var difficultyGroup: RadioGroup
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var blackBoxSwitch: SwitchCompat
    private val uiHandler = Handler(Looper.getMainLooper())
    private var nextSwitchAtElapsed: Long = -1L

    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateTimeRemaining()
            uiHandler.postDelayed(this, 1_000L)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(RandomTimerService.EXTRA_STATUS)
            statusText.text = status ?: getString(R.string.status_idle)
            nextSwitchAtElapsed = intent.getLongExtra(
                RandomTimerService.EXTRA_NEXT_SWITCH_AT,
                -1L
            )
            updateTimeRemaining()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        timeRemainingText = findViewById(R.id.timeRemainingText)
        difficultyGroup = findViewById(R.id.difficultyGroup)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        blackBoxSwitch = findViewById(R.id.blackBoxSwitch)

        findViewById<RadioButton>(R.id.difficultyEasy).isChecked = true
        blackBoxSwitch.setOnCheckedChangeListener { _, _ ->
            updateTimeVisibility()
        }

        startButton.setOnClickListener {
            if (!canPostNotifications()) {
                Toast.makeText(
                    this,
                    R.string.notification_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
                requestNotificationPermissionIfNeeded()
                updateStartButtonState()
                return@setOnClickListener
            }
            val level = when (difficultyGroup.checkedRadioButtonId) {
                R.id.difficultyMedium -> DifficultyLevel.MEDIUM
                R.id.difficultyHard -> DifficultyLevel.HARD
                else -> DifficultyLevel.EASY
            }
            val intent = Intent(this, RandomTimerService::class.java).apply {
                action = RandomTimerService.ACTION_START
                putExtra(RandomTimerService.EXTRA_LEVEL, level.name)
            }
            ContextCompat.startForegroundService(this, intent)
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, RandomTimerService::class.java).apply {
                action = RandomTimerService.ACTION_STOP
            }
            startService(intent)
            statusText.text = getString(R.string.status_idle)
            nextSwitchAtElapsed = -1L
            updateTimeRemaining()
        }

        requestNotificationPermissionIfNeeded()
        updateStartButtonState()
        updateTimeVisibility()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(statusReceiver, IntentFilter(RandomTimerService.ACTION_STATUS))
        uiHandler.post(countdownRunnable)
    }

    override fun onResume() {
        super.onResume()
        updateStartButtonState()
    }

    override fun onStop() {
        uiHandler.removeCallbacks(countdownRunnable)
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED
        ) {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        }
        updateStartButtonState()
    }

    private fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateStartButtonState() {
        startButton.isEnabled = canPostNotifications()
    }

    private fun updateTimeRemaining() {
        if (blackBoxSwitch.isChecked) {
            return
        }
        val remainingMillis = nextSwitchAtElapsed - android.os.SystemClock.elapsedRealtime()
        if (nextSwitchAtElapsed <= 0L || remainingMillis <= 0L) {
            timeRemainingText.text = getString(R.string.time_remaining_idle)
            return
        }
        val totalSeconds = remainingMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        timeRemainingText.text = getString(R.string.time_remaining_format, formatted)
    }

    private fun updateTimeVisibility() {
        if (blackBoxSwitch.isChecked) {
            timeRemainingText.visibility = android.view.View.GONE
        } else {
            timeRemainingText.visibility = android.view.View.VISIBLE
            updateTimeRemaining()
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
    }
}
