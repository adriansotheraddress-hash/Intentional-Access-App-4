package com.example.intentionalaccess

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Views cached in onCreate — not looked up again on every onResume
    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var overlayButton: Button
    private lateinit var startButton: Button
    private lateinit var settingsLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText       = findViewById(R.id.statusText)
        permissionButton = findViewById(R.id.permissionButton)
        overlayButton    = findViewById(R.id.overlayButton)
        startButton      = findViewById(R.id.startButton)
        settingsLink     = findViewById(R.id.settingsLink)

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        overlayButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        startButton.setOnClickListener {
            if (hasUsagePermission() && hasOverlayPermission()) {
                startForegroundService(Intent(this, AppMonitorService::class.java))
                // onStartCommand sets the KEY_SERVICE_RUNNING flag — updateUI will
                // read it on the next onResume; update optimistically here too
                statusText.text = "✓ Intentional Access is active"
                statusText.setTextColor(COLOR_GREEN)
                startButton.text = "Running"
                startButton.isEnabled = false
            } else {
                statusText.text = "Please grant both permissions first."
                statusText.setTextColor(COLOR_RED)
            }
        }

        settingsLink.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    // ── UI state ─────────────────────────────────────────────────────────────

    private fun updateUI() {
        val usageGranted   = hasUsagePermission()
        val overlayGranted = hasOverlayPermission()
        val serviceRunning = isServiceRunning()

        permissionButton.text      = if (usageGranted)   "✓ Usage Access Granted"    else "1. Grant Usage Access"
        permissionButton.isEnabled = !usageGranted

        overlayButton.text      = if (overlayGranted) "✓ Overlay Permission Granted" else "2. Grant Overlay Permission"
        overlayButton.isEnabled = !overlayGranted

        when {
            serviceRunning -> {
                startButton.text      = "Running"
                startButton.isEnabled = false
                statusText.text       = "✓ Intentional Access is active"
                statusText.setTextColor(COLOR_GREEN)
            }
            usageGranted && overlayGranted -> {
                startButton.isEnabled = true
                statusText.text       = "Ready to start."
                statusText.setTextColor(COLOR_GREY)
            }
            else -> {
                startButton.isEnabled = false
                statusText.text       = "Grant permissions above to get started."
                statusText.setTextColor(COLOR_GREY)
            }
        }
    }

    // ── permission checks ────────────────────────────────────────────────────

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode   = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun isServiceRunning(): Boolean =
        getSharedPreferences(AppMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AppMonitorService.KEY_SERVICE_RUNNING, false)

    // ── constants ────────────────────────────────────────────────────────────

    companion object {
        private const val COLOR_GREEN = 0xFF4ADE80.toInt()
        private const val COLOR_RED   = 0xFFF87171.toInt()
        private const val COLOR_GREY  = 0xFF888888.toInt()
    }
}
