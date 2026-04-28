package com.example.intentionalaccess

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class IntentGateActivity : Activity() {

    private lateinit var intentInput: EditText
    private lateinit var submitButton: Button
    private lateinit var wordCountText: TextView
    private lateinit var appNameText: TextView
    private var blockedPackage: String = ""
    private var pasteLength: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_intent_gate)

        blockedPackage = intent.getStringExtra("blocked_package") ?: ""
        val appName = intent.getStringExtra("app_name") ?: "this app"

        intentInput = findViewById(R.id.intentInput)
        submitButton = findViewById(R.id.submitButton)
        wordCountText = findViewById(R.id.wordCountText)
        appNameText = findViewById(R.id.appNameText)

        appNameText.text = "You're trying to open $appName"

        submitButton.isEnabled = false

        intentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                pasteLength = s?.length ?: 0
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Detect paste: if more than 10 chars added at once, it's likely a paste
                val added = (s?.length ?: 0) - pasteLength
                if (added > 10) {
                    intentInput.setText("")
                    Toast.makeText(this@IntentGateActivity,
                        "Please type your intent — don't paste it.", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            override fun afterTextChanged(s: Editable?) {
                val words = s.toString().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val count = words.size
                wordCountText.text = "$count / 10 words minimum"
                wordCountText.setTextColor(
                    if (count >= 10) 0xFF4ADE80.toInt() else 0xFFF87171.toInt()
                )
                submitButton.isEnabled = count >= 10 && !isSpam(s.toString())
            }
        })

        submitButton.setOnClickListener {
            val intentText = intentInput.text.toString().trim()
            saveSession(intentText, blockedPackage)
            val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("unlocked_package", blockedPackage)
                .putLong("unlocked_at", System.currentTimeMillis())
                .apply()
            // Relaunch the target app directly
            val launchIntent = packageManager.getLaunchIntentForPackage(blockedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
            finish()
        }
    }

    override fun onBackPressed() {
        // Do nothing — can't dismiss without submitting
    }

    private fun isSpam(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val uniqueWords = words.toSet()
        // Too many repeated words
        if (words.size > 5 && uniqueWords.size.toFloat() / words.size < 0.4f) return true
        // Keyboard mash
        if (Regex("[asdfghjkl]{6,}|[qwertyuiop]{6,}|[zxcvbnm]{6,}").containsMatchIn(lower)) return true
        return false
    }

    private fun saveSession(intentText: String, packageName: String) {
        val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
        val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"
        val timestamp = System.currentTimeMillis()
        val newEntry = """{"intent":"$intentText","package":"$packageName","time":$timestamp,"accomplished":null}"""
        val updated = sessionsJson.dropLast(1) +
                (if (sessionsJson == "[]") "" else ",") + newEntry + "]"
        prefs.edit().putString("sessions", updated).apply()
    }

    companion object {
        fun launch(context: Context, packageName: String, appName: String) {
            val intent = Intent(context, IntentGateActivity::class.java).apply {
                putExtra("blocked_package", packageName)
                putExtra("app_name", appName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}