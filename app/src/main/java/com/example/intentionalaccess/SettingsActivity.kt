package com.example.intentionalaccess

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        val container   = findViewById<LinearLayout>(R.id.appToggleContainer)
        val enabledApps = BlockedAppsConfig.getEnabledApps(this).keys

        for ((packageName, appName) in BlockedAppsConfig.DEFAULT_APPS) {
            addAppRow(container, packageName, appName, packageName in enabledApps)
        }
    }

    // ── row builder ──────────────────────────────────────────────────────────

    @Suppress("DEPRECATION") // Switch — fine for minSdk 24 without Material dep upgrade
    private fun addAppRow(
        container: LinearLayout,
        packageName: String,
        appName: String,
        isEnabled: Boolean
    ) {
        val rowPaddingV = dp(16)

        // ── label + toggle row ───────────────────────────────────────────────
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, rowPaddingV, 0, rowPaddingV)
        }

        val nameView = TextView(this).apply {
            text = appName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFF0F0F0.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val toggle = Switch(this).apply {
            isChecked = isEnabled
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                BlockedAppsConfig.toggle(this@SettingsActivity, packageName, checked)
            }
        }

        row.addView(nameView)
        row.addView(toggle)
        container.addView(row)

        // ── 1dp divider ─────────────────────────────────────────────────────
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(0xFF1A1A1A.toInt())
        })
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Converts [value] dp to pixels using the current display metrics. */
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
