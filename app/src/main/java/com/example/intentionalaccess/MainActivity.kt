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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val permissionButton = findViewById<Button>(R.id.permissionButton)
        val overlayButton = findViewById<Button>(R.id.overlayButton)
        val startButton = findViewById<Button>(R.id.startButton)

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
                statusText.text = "✓ Intentional Access is active"
                statusText.setTextColor(0xFF4ADE80.toInt())
                startButton.text = "Running"
                startButton.isEnabled = false
            } else {
                statusText.text = "Please grant both permissions first."
                statusText.setTextColor(0xFFF87171.toInt())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val permissionButton = findViewById<Button>(R.id.permissionButton)
        val overlayButton = findViewById<Button>(R.id.overlayButton)
        val startButton = findViewById<Button>(R.id.startButton)

        if (hasUsagePermission()) {
            permissionButton.text = "✓ Usage Access Granted"
            permissionButton.isEnabled = false
        }

        if (hasOverlayPermission()) {
            overlayButton.text = "✓ Overlay Permission Granted"
            overlayButton.isEnabled = false
        }

        if (hasUsagePermission() && hasOverlayPermission()) {
            startButton.isEnabled = true
            statusText.text = "Ready to start."
            statusText.setTextColor(0xFF888888.toInt())
        }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
}