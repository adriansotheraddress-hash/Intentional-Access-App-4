package com.example.intentionalaccess

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L
    private var lastBlockedApp = ""
    private var isMonitoring = false

    private val blockedApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.twitter.android" to "Twitter",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.reddit.frontpage" to "Reddit",
        "com.google.android.youtube" to "YouTube",
        "com.snapchat.android" to "Snapchat",
        "com.linkedin.android" to "LinkedIn",
        "com.threads.android" to "Threads"
    )

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", true).apply()
        if (!isMonitoring) {
            isMonitoring = true
            handler.post(monitorRunnable)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
        getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", false).apply()
        super.onDestroy()
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - 5000, now
        )

        if (stats.isNullOrEmpty()) return

        val foregroundApp = stats
            .filter { it.lastTimeUsed > now - 3000 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        if (!blockedApps.containsKey(foregroundApp)) {
            lastBlockedApp = ""
            return
        }

        val appName = blockedApps[foregroundApp] ?: foregroundApp
        val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
        val unlockedPackage = prefs.getString("unlocked_package", "") ?: ""
        val unlockedAt = prefs.getLong("unlocked_at", 0L)
        val sessionDuration = 60 * 60 * 1000L

        val sessionExpired = unlockedPackage == foregroundApp &&
                (now - unlockedAt) >= sessionDuration

        val isUnlocked = unlockedPackage == foregroundApp &&
                (now - unlockedAt) < sessionDuration

        if (sessionExpired) {
            if (foregroundApp == lastBlockedApp) return
            lastBlockedApp = foregroundApp
            val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"
            val lastIntent = extractLastIntent(sessionsJson, foregroundApp)
            CheckInActivity.launch(this, foregroundApp, appName, lastIntent)
            return
        }

        if (isUnlocked) {
            lastBlockedApp = ""
            return
        }

        if (foregroundApp == lastBlockedApp) return

        lastBlockedApp = foregroundApp
        IntentGateActivity.launch(this, foregroundApp, appName)
    }

    private fun extractLastIntent(sessionsJson: String, packageName: String): String {
        return try {
            val array = org.json.JSONArray(sessionsJson)
            for (i in array.length() - 1 downTo 0) {
                val obj = array.getJSONObject(i)
                if (obj.getString("package") == packageName) {
                    return obj.getString("intent")
                }
            }
            "do what you came for"
        } catch (e: Exception) {
            "do what you came for"
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "intentional_access_monitor"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring app usage for intentional access"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Cotter")
                .setContentText("Watching for high-stimulation apps")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Cotter")
                .setContentText("Watching for high-stimulation apps")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        }
    }
}