package com.example.intentionalaccess

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L // check every second
    private var lastBlockedApp = ""
    private var lastUnlockedPackage = ""
    private var lastUnlockedAt = 0L

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
        handler.post(monitorRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(monitorRunnable)
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

        // Check if this app is currently unlocked
        val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
        val unlockedPackage = prefs.getString("unlocked_package", "") ?: ""
        val unlockedAt = prefs.getLong("unlocked_at", 0L)
        val sessionDuration = 60 * 60 * 1000L // 1 hour

        val isUnlocked = unlockedPackage == foregroundApp &&
                (now - unlockedAt) < sessionDuration

        if (isUnlocked) {
            lastBlockedApp = ""
            return
        }

        // Avoid showing the gate repeatedly for the same app
        if (foregroundApp == lastBlockedApp) return

        lastBlockedApp = foregroundApp
        val appName = blockedApps[foregroundApp] ?: foregroundApp
        IntentGateActivity.launch(this, foregroundApp, appName)
    }

    private fun buildNotification(): Notification {
        val channelId = "intentional_access_monitor"
        val channel = NotificationChannel(
            channelId,
            "App Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring app usage for intentional access"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Intentional Access")
            .setContentText("Watching for high-stimulation apps")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }
}