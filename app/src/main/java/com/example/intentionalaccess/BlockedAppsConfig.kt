package com.example.intentionalaccess

import android.content.Context
import org.json.JSONObject

/**
 * Single source of truth for which apps Intentional Access monitors.
 *
 * The default list ships with the app. Users can toggle individual entries
 * on or off via SettingsActivity; the enabled set is persisted in
 * SharedPreferences as a JSON object  {packageName: Boolean, ...}.
 *
 * Opt-out model: any package not yet present in the stored config
 * is treated as enabled, so newly added packages in a future update
 * are active by default.
 */
object BlockedAppsConfig {

    /** Canonical package → display-name map, ordered for the settings screen. */
    val DEFAULT_APPS: Map<String, String> = linkedMapOf(
        "com.instagram.android"       to "Instagram",
        "com.twitter.android"         to "Twitter / X",
        "com.zhiliaoapp.musically"    to "TikTok",
        "com.facebook.katana"         to "Facebook",
        "com.reddit.frontpage"        to "Reddit",
        "com.google.android.youtube"  to "YouTube",
        "com.snapchat.android"        to "Snapchat",
        "com.linkedin.android"        to "LinkedIn",
        "com.threads.android"         to "Threads"
    )

    private const val PREFS_NAME     = "intentional_access"
    private const val KEY_APP_CONFIG = "blocked_apps_config"

    // ── read ─────────────────────────────────────────────────────────────────

    /**
     * Returns the subset of [DEFAULT_APPS] that the user has left enabled.
     * On first run (no stored config) this equals [DEFAULT_APPS].
     */
    fun getEnabledApps(context: Context): Map<String, String> {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_CONFIG, null)
            ?: return DEFAULT_APPS   // first run — everything on

        return try {
            val json = JSONObject(raw)
            DEFAULT_APPS.filter { (pkg, _) -> json.optBoolean(pkg, true) }
        } catch (_: Exception) {
            DEFAULT_APPS             // corrupted config — safe fallback
        }
    }

    // ── write ────────────────────────────────────────────────────────────────

    /**
     * Persists which packages are currently enabled.
     * Packages in [enabledPackages] are stored as `true`; all others as `false`.
     */
    fun saveConfig(context: Context, enabledPackages: Set<String>) {
        val json = JSONObject()
        DEFAULT_APPS.keys.forEach { pkg -> json.put(pkg, pkg in enabledPackages) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_CONFIG, json.toString())
            .apply()
    }

    /**
     * Flips a single app on or off without touching the rest of the config.
     */
    fun toggle(context: Context, packageName: String, enabled: Boolean) {
        // Read the full current enabled set, mutate, and write back atomically
        val current = getEnabledApps(context).keys.toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        saveConfig(context, current)
    }
}
