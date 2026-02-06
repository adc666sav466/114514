package com.example.randomtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var difficultyGroup: RadioGroup
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(RandomTimerService.EXTRA_STATUS)
            statusText.text = status ?: getString(R.string.status_idle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        difficultyGroup = findViewById(R.id.difficultyGroup)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        findViewById<RadioButton>(R.id.difficultyEasy).isChecked = true

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
        }

        requestNotificationPermissionIfNeeded()
        updateStartButtonState()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(statusReceiver, IntentFilter(RandomTimerService.ACTION_STATUS))
    }

    override fun onResume() {
        super.onResume()
        updateStartButtonState()
    }

    override fun onStop() {
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

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
    }
}
