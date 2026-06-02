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

    // Guard against duplicate monitoring loops if onStartCommand is called
    // more than once while the service is already running (START_STICKY restart,
    // or the user tapping Start again without the app checking the running state).
    private var isMonitoring = false

    // Track which app last triggered the gate so we don't spam it
    private var lastBlockedApp = ""

    // ── blocked-app config cache ─────────────────────────────────────────────
    // Refreshed from SharedPreferences at most once every CONFIG_CACHE_TTL_MS.
    // Avoids parsing JSON on every 1-second poll tick.
    private var cachedBlockedApps: Map<String, String> = emptyMap()
    private var configCachedAt: Long = 0L

    private fun getBlockedApps(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (now - configCachedAt > CONFIG_CACHE_TTL_MS) {
            cachedBlockedApps = BlockedAppsConfig.getEnabledApps(this)
            configCachedAt = now
        }
        return cachedBlockedApps
    }

    // ── polling loop ─────────────────────────────────────────────────────────

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Only start the polling loop once — safe even if onStartCommand fires again
        if (!isMonitoring) {
            isMonitoring = true
            // Persist the running state so MainActivity can reflect it correctly
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SERVICE_RUNNING, true).apply()
            handler.post(monitorRunnable)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(monitorRunnable)
        isMonitoring = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_RUNNING, false).apply()
        super.onDestroy()
    }

    // ── core check ───────────────────────────────────────────────────────────

    private fun checkForegroundApp() {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        // queryUsageStats can throw SecurityException if permission was revoked
        // at runtime, or throw other exceptions on certain OEM builds.
        val stats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - USAGE_WINDOW_MS,
                now
            )
        } catch (_: Exception) {
            return  // Permission gone or API error — skip this tick silently
        }

        if (stats.isNullOrEmpty()) return

        val foregroundApp = stats
            .filter { it.lastTimeUsed > now - USAGE_FRESHNESS_MS }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        val blockedApps = getBlockedApps()

        if (!blockedApps.containsKey(foregroundApp)) {
            // User navigated away from a blocked app — reset so the gate can
            // fire again if they return before the session expires
            lastBlockedApp = ""
            return
        }

        // Check whether this app has an active unlocked session
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlockedPackage = prefs.getString(KEY_UNLOCKED_PACKAGE, "") ?: ""
        val unlockedAt      = prefs.getLong(KEY_UNLOCKED_AT, 0L)

        val isUnlocked = unlockedPackage == foregroundApp &&
                (now - unlockedAt) < SESSION_DURATION_MS

        if (isUnlocked) {
            lastBlockedApp = ""
            return
        }

        // Deduplication — don't re-launch the gate for the same app on every tick
        if (foregroundApp == lastBlockedApp) return

        lastBlockedApp = foregroundApp
        val appName = blockedApps[foregroundApp] ?: foregroundApp
        IntentGateActivity.launch(this, foregroundApp, appName)
    }

    // ── notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val channelId = "intentional_access_monitor"
        val channel = NotificationChannel(
            channelId,
            "App Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Monitoring app usage for intentional access" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Intentional Access")
            .setContentText("Watching for high-stimulation apps")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }

    // ── constants ────────────────────────────────────────────────────────────

    companion object {
        private const val NOTIFICATION_ID      = 1
        private const val CHECK_INTERVAL_MS    = 1_000L          // poll every 1 s
        private const val USAGE_WINDOW_MS      = 5_000L          // query last 5 s of usage data
        private const val USAGE_FRESHNESS_MS   = 3_000L          // "foreground" = used within 3 s
        private const val SESSION_DURATION_MS  = 60 * 60 * 1_000L // 1-hour unlock window
        private const val CONFIG_CACHE_TTL_MS  = 30_000L         // re-read config every 30 s

        const val PREFS_NAME          = "intentional_access"
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_UNLOCKED_PACKAGE = "unlocked_package"
        const val KEY_UNLOCKED_AT     = "unlocked_at"
    }
}
