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
    private val apiKey = BuildConfig.API_KEY
    private var isValidating = false

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

        blockedPackage = getIntent().getStringExtra("blocked_package") ?: ""
        val appName = getIntent().getStringExtra("app_name") ?: "this app"

        intentInput = findViewById(R.id.intentInput)
        submitButton = findViewById(R.id.submitButton)
        wordCountText = findViewById(R.id.wordCountText)
        appNameText = findViewById(R.id.appNameText)

        appNameText.text = "● OPENING ${appName.uppercase()}"

        submitButton.isEnabled = false

        intentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                pasteLength = s?.length ?: 0
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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
                val freshnessText = findViewById<TextView>(R.id.freshnessText)
                wordCountText.text = "$count / 10 words"
                wordCountText.setTextColor(
                    if (count >= 10) 0xFF7BAF8E.toInt() else 0xFFC4A8C8.toInt()
                )
                val notSpam = !isSpam(s.toString())
                if (count >= 10 && notSpam) {
                    freshnessText.text = "● fresh & specific"
                    freshnessText.setTextColor(0xFF7BAF8E.toInt())
                } else {
                    freshnessText.text = ""
                }
                submitButton.isEnabled = count >= 10 && notSpam
            }
        })

        submitButton.setOnClickListener {
            if (isValidating) return@setOnClickListener
            val intentText = intentInput.text.toString().trim()
            isValidating = true
            submitButton.isEnabled = false
            submitButton.text = "Checking..."

            validateWithClaude(intentText) { isValid, reason ->
                isValidating = false
                if (isValid) {
                    saveSession(intentText, blockedPackage)
                    val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("unlocked_package", blockedPackage)
                        .putLong("unlocked_at", System.currentTimeMillis())
                        .apply()
                    val launchIntent = packageManager.getLaunchIntentForPackage(blockedPackage)
                    android.util.Log.d("Cotter", "Launching package: $blockedPackage")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(launchIntent)
                    }
                    finishAffinity()
                } else {
                    submitButton.isEnabled = true
                    submitButton.text = "Unlock for 1 hour"
                    val errorView = findViewById<TextView>(R.id.freshnessText)
                    errorView.text = "✕ $reason"
                    errorView.setTextColor(0xFFD47A6A.toInt())
                }
            }
        }

        findViewById<TextView>(R.id.cancelText).setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Do nothing — can't dismiss without submitting
    }
    private fun validateWithClaude(intentText: String, onResult: (Boolean, String) -> Unit) {
        val prefs = getSharedPreferences("intentional_access", Context.MODE_PRIVATE)
        val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"

        val prompt = """You are validating a user's stated intent before they open a social media app.

The user was asked: "What is your intent for this session?"
Their answer: "$intentText"

Past intents for this app (check for similarity):
$sessionsJson

Check ALL of the following:
1. Does it actually answer the question "what is your intent"? It should describe a specific purpose.
2. Is it semantically unique from past intents — not just reworded versions of the same thing?
3. Is it genuine and not nonsense?

Reply in this exact format:
VALID: true or false
REASON: one short sentence explaining why if false, or "looks good" if true"""

        Thread {
            try {
                val url = java.net.URL("https://api.anthropic.com/v1/messages")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.doOutput = true

                val body = """
                    {
                        "model": "claude-haiku-4-5-20251001",
                        "max_tokens": 100,
                        "messages": [{"role": "user", "content": ${org.json.JSONObject.quote(prompt)}}]
                    }
                """.trimIndent()

                connection.outputStream.write(body.toByteArray())
                connection.outputStream.flush()

                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val content = json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")

                val isValid = content.contains("VALID: true", ignoreCase = true)
                val reasonLine = content.lines().find { it.startsWith("REASON:") }
                val reason = reasonLine?.removePrefix("REASON:")?.trim() ?: "Please try again."

                runOnUiThread { onResult(isValid, reason) }
            } catch (e: Exception) {
                runOnUiThread { onResult(true, "looks good") } // fail open
            }
        }.start()
    }
    private fun isSpam(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val uniqueWords = words.toSet()
        if (words.size > 5 && uniqueWords.size.toFloat() / words.size < 0.4f) return true
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