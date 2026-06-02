package com.example.intentionalaccess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentValidatorTest {

    // ── isSpam: repetition ────────────────────────────────────────────────────

    @Test
    fun `isSpam returns true for heavily repeated words`() {
        // "check check check check check check" — all the same word
        val text = "check check check check check check check check check check"
        assertTrue(IntentValidator.isSpam(text))
    }

    @Test
    fun `isSpam returns false for natural varied text`() {
        val text = "I want to catch up on messages from my close friends and family"
        assertFalse(IntentValidator.isSpam(text))
    }

    @Test
    fun `isSpam allows some repetition below threshold`() {
        // "the" repeated but uniqueRatio still > 0.4
        val text = "I want to check the news and the weather and the scores"
        assertFalse(IntentValidator.isSpam(text))
    }

    // ── isSpam: keyboard mash ─────────────────────────────────────────────────

    @Test
    fun `isSpam returns true for home-row mash (asdf)`() {
        val text = "asdfghjkl I want to use the app for something"
        assertTrue(IntentValidator.isSpam(text))
    }

    @Test
    fun `isSpam returns true for top-row mash (qwer)`() {
        val text = "qwertyuiop just mashing the keyboard here"
        assertTrue(IntentValidator.isSpam(text))
    }

    @Test
    fun `isSpam returns true for bottom-row mash (zxcv)`() {
        val text = "zxcvbnmzxcvbnm some other words after"
        assertTrue(IntentValidator.isSpam(text))
    }

    @Test
    fun `isSpam returns false for empty string`() {
        assertFalse(IntentValidator.isSpam(""))
    }

    @Test
    fun `isSpam returns false for fewer than 6 words even if all identical`() {
        // Repetition check only fires at words.size > 5
        val text = "check check check check"
        assertFalse(IntentValidator.isSpam(text))
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    fun `isValid returns false when fewer than 10 words`() {
        val text = "I want to check my messages"
        assertFalse(IntentValidator.isValid(text))
    }

    @Test
    fun `isValid returns true for well-formed 10-word intent`() {
        val text = "I want to check if my friend replied about the weekend plans"
        assertTrue(IntentValidator.isValid(text))
    }

    @Test
    fun `isValid returns false for spammy text even with enough words`() {
        val text = "check check check check check check check check check check check"
        assertFalse(IntentValidator.isValid(text))
    }

    @Test
    fun `isValid counts words correctly across multiple spaces`() {
        // Extra whitespace should not inflate word count
        val text = "I  want  to  check  messages"   // only 5 words despite spaces
        assertFalse(IntentValidator.isValid(text))
    }

    @Test
    fun `MIN_WORD_COUNT is 10`() {
        // Confirm the constant itself, so a casual change doesn't slip through
        assert(IntentValidator.MIN_WORD_COUNT == 10)
    }
}
