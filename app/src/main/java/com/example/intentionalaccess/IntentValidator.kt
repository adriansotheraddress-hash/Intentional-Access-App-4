package com.example.intentionalaccess

/**
 * Stateless validation rules for intent text.
 *
 * Extracted from IntentGateActivity so the logic can be unit-tested
 * independently of the Android Activity lifecycle.
 */
object IntentValidator {

    const val MIN_WORD_COUNT = 10

    private val KEYBOARD_MASH_PATTERN =
        Regex("[asdfghjkl]{6,}|[qwertyuiop]{6,}|[zxcvbnm]{6,}")

    /**
     * Returns true if [text] looks like it was keyboard-mashed or robotically
     * repeated rather than genuinely typed.
     *
     * Rules:
     *   - Fewer than 40 % of words are unique (lots of repetition)
     *   - Contains a long run along a keyboard row (asdf…, qwer…, zxcv…)
     */
    fun isSpam(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false

        // Repetition check — only meaningful once we have enough words
        if (words.size > 5) {
            val uniqueRatio = words.toSet().size.toFloat() / words.size
            if (uniqueRatio < 0.4f) return true
        }

        // Keyboard-row mash check
        if (KEYBOARD_MASH_PATTERN.containsMatchIn(lower)) return true

        return false
    }

    /**
     * Returns true if [text] clears the minimum quality bar:
     * at least [MIN_WORD_COUNT] words and not flagged as spam.
     */
    fun isValid(text: String): Boolean {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return words.size >= MIN_WORD_COUNT && !isSpam(text)
    }
}
