package com.example.intentionalaccess

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class CheckInActivity : Activity() {

    private var blockedPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_check_in)

        blockedPackage = intent.getStringExtra("blocked_package") ?: ""
        val originalIntent = intent.getStringExtra("original_intent") ?: ""
        val appName = intent.getStringExtra("app_name") ?: "this app"

        findViewById<TextView>(R.id.checkinAppName).text = "● 1 HOUR LATER · ${appName.uppercase()}"
        findViewById<TextView>(R.id.checkinIntentText).text =
            "You set out to $originalIntent.\n\nDid you do it?"

        findViewById<Button>(R.id.btnYes).setOnClickListener {
            saveOutcome("yes")
            relaunchApp()
        }

        findViewById<Button>(R.id.btnPartly).setOnClickListener {
            saveOutcome("partly")
            relaunchApp()
        }

        findViewById<Button>(R.id.btnNo).setOnClickListener {
            saveOutcome("no")
            goHome()
        }
    }

    override fun onBackPressed() { }

    private fun saveOutcome(outcome: String) {
        val prefs = getSharedPreferences("intentional_access", MODE_PRIVATE)
        val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"
        try {
            val array = org.json.JSONArray(sessionsJson)
            for (i in array.length() - 1 downTo 0) {
                val obj = array.getJSONObject(i)
                if (obj.optString("package") == blockedPackage && obj.isNull("accomplished")) {
                    obj.put("accomplished", outcome)
                    break
                }
            }
            prefs.edit().putString("sessions", array.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("Cotter", "Failed to save check-in outcome", e)
        }
        prefs.edit()
            .remove("unlocked_package")
            .remove("unlocked_at")
            .apply()
    }

    private fun relaunchApp() {
        val prefs = getSharedPreferences("intentional_access", MODE_PRIVATE)
        prefs.edit()
            .putString("unlocked_package", blockedPackage)
            .putLong("unlocked_at", System.currentTimeMillis())
            .apply()
        val launchIntent = packageManager.getLaunchIntentForPackage(blockedPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
        finish()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        fun launch(context: Context, packageName: String, appName: String, originalIntent: String) {
            val intent = Intent(context, CheckInActivity::class.java).apply {
                putExtra("blocked_package", packageName)
                putExtra("app_name", appName)
                putExtra("original_intent", originalIntent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}