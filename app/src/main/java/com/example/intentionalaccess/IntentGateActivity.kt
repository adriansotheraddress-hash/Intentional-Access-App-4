package com.example.intentionalaccess

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class IntentGateActivity : Activity() {

    private lateinit var intentInput:   EditText
    private lateinit var submitButton:  Button
    private lateinit var wordCountText: TextView
    private lateinit var appNameText:   TextView
    private lateinit var feedbackText:  TextView

    private var blockedPackage: String = ""

    // Single-thread executor for DB reads + similarity computation.
    // Keeps those operations off the main thread without pulling in coroutines.
    private val executor = Executors.newSingleThreadExecutor()

    // Track the text length just before a change so we can detect paste events
    private var lengthBeforeChange: Int = 0

    // ── lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and keep it lit
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON        or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD      or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED      or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_intent_gate)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName    = intent.getStringExtra(EXTRA_APP_NAME) ?: "this app"

        intentInput   = findViewById(R.id.intentInput)
        submitButton  = findViewById(R.id.submitButton)
        wordCountText = findViewById(R.id.wordCountText)
        appNameText   = findViewById(R.id.appNameText)
        feedbackText  = findViewById(R.id.feedbackText)

        appNameText.text       = "You're trying to open $appName"
        submitButton.isEnabled = false

        intentInput.addTextChangedListener(intentWatcher)
        submitButton.setOnClickListener { onSubmitClicked() }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Intentionally blocked — the user must submit an intent to proceed
    }

    // ── text watcher ──────────────────────────────────────────────────────────

    private val intentWatcher = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            lengthBeforeChange = s?.length ?: 0
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val added = (s?.length ?: 0) - lengthBeforeChange
            if (added > 10) {
                intentInput.setText("")
                Toast.makeText(
                    this@IntentGateActivity,
                    "Please type your intent — don't paste it.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun afterTextChanged(s: Editable?) {
            // Hide any previous similarity rejection while the user is editing
            feedbackText.visibility = View.GONE

            val words = s.toString().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val count = words.size

            wordCountText.text = "$count / $MIN_WORD_COUNT words minimum"
            wordCountText.setTextColor(if (count >= MIN_WORD_COUNT) COLOR_GREEN else COLOR_RED)

            submitButton.isEnabled = count >= MIN_WORD_COUNT && !IntentValidator.isSpam(s.toString())
        }
    }

    // ── submit ────────────────────────────────────────────────────────────────

    private fun onSubmitClicked() {
        val intentText = intentInput.text.toString().trim()

        // Disable the button immediately to prevent double-taps while the
        // background check is running
        submitButton.isEnabled = false
        feedbackText.visibility = View.GONE

        executor.execute {
            // ── Off main thread: DB read + similarity check ──────────────────
            val db          = IntentSessionDb.getInstance(applicationContext)
            val pastIntents = db.getRecentIntents(blockedPackage, IntentSimilarityChecker.MAX_HISTORY)
            val tooSimilar  = IntentSimilarityChecker.isTooSimilar(intentText, pastIntents)

            if (!tooSimilar) {
                // Persist before switching back to the main thread so the
                // record is safely written even if the Activity is finishing
                db.insertSession(blockedPackage, intentText)
            }

            // ── Back on main thread: update UI or launch ─────────────────────
            runOnUiThread {
                if (tooSimilar) {
                    feedbackText.text =
                        "Too similar to a previous intent. " +
                        "Be specific about what's different this time."
                    feedbackText.visibility = View.VISIBLE
                    // Re-enable so the user can revise and try again
                    submitButton.isEnabled = true
                } else {
                    unlockAndLaunch()
                }
            }
        }
    }

    // ── unlock + launch ───────────────────────────────────────────────────────

    private fun unlockAndLaunch() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNLOCKED_PACKAGE, blockedPackage)
            .putLong(KEY_UNLOCKED_AT, System.currentTimeMillis())
            .apply()

        packageManager.getLaunchIntentForPackage(blockedPackage)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?.let { startActivity(it) }

        finish()
    }

    // ── companion ─────────────────────────────────────────────────────────────

    companion object {
        private val MIN_WORD_COUNT = IntentValidator.MIN_WORD_COUNT

        private const val PREFS_NAME           = "intentional_access"
        private const val KEY_UNLOCKED_PACKAGE = "unlocked_package"
        private const val KEY_UNLOCKED_AT      = "unlocked_at"

        private const val EXTRA_PACKAGE  = "blocked_package"
        private const val EXTRA_APP_NAME = "app_name"

        private const val COLOR_GREEN = 0xFF4ADE80.toInt()
        private const val COLOR_RED   = 0xFFF87171.toInt()

        fun launch(context: Context, packageName: String, appName: String) {
            val intent = Intent(context, IntentGateActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE,  packageName)
                putExtra(EXTRA_APP_NAME, appName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}
