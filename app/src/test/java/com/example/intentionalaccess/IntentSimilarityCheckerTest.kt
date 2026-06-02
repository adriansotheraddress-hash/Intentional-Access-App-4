package com.example.intentionalaccess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IntentSimilarityChecker.isTooSimilar].
 *
 * These run on the JVM (no Android runtime needed) because
 * [IntentSimilarityChecker] has zero Android dependencies.
 *
 * [IntentSessionDb] integration (DB read → similarity check flow) is
 * covered by instrumented tests in androidTest/.
 */
class IntentSimilarityCheckerTest {

    // ── identical / exact copy ────────────────────────────────────────────────

    @Test
    fun `identical intents are rejected`() {
        val text = "I want to check if my friend replied about Saturday plans on Instagram"
        assertTrue(IntentSimilarityChecker.isTooSimilar(text, listOf(text)))
    }

    // ── synonym paraphrases ── the core new behaviour ─────────────────────────

    @Test
    fun `DMs vs messages paraphrase is rejected`() {
        val current = "I need to look at my DMs to see if my friend responded"
        val past    = "I want to check my messages to see if my buddy replied"
        // After synonym normalisation both collapse to {messages, friend, reply}
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `replied vs responded vs answered paraphrase is rejected`() {
        val current = "I want to check if my colleague answered my question about the project"
        val past    = "I need to see if my coworker responded to my question about the project"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `share vs upload vs post paraphrase is rejected`() {
        val current = "I am going to upload the concert photos from last night"
        val past    = "I want to share the concert pictures from last night"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `watch video vs see reel paraphrase is rejected`() {
        val current = "I want to watch a cooking tutorial for dinner tonight"
        val past    = "I need to see a cooking video for dinner tonight"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `scroll feed vs browse feed paraphrase is rejected`() {
        val current = "I want to scroll through my feed to catch up on the latest news"
        val past    = "I need to browse my feed to catch up on the latest updates"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    // ── reordering ────────────────────────────────────────────────────────────

    @Test
    fun `reordered words with same vocabulary is rejected`() {
        val current = "research pasta carbonara recipe cook dinner tonight"
        val past    = "cook dinner tonight research pasta carbonara recipe"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    // ── genuinely different intents ───────────────────────────────────────────

    @Test
    fun `completely different intents are allowed`() {
        val current = "I want to research a recipe for pasta carbonara to cook for dinner"
        val past    = "I need to reply to my project manager about the quarterly report deadline"
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `same platform different purpose is allowed`() {
        val current = "I want to upload photos from my birthday party last weekend"
        val past    = "I need to reply to my friend about the hiking plans for Sunday"
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    @Test
    fun `watching sports vs checking messages is allowed`() {
        val current = "I want to watch the football highlights from last night match"
        val past    = "I need to check my messages to see if my friend replied about plans"
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty past list always allows`() {
        val current = "I want to check if my friend replied about the weekend plans tonight"
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, emptyList()))
    }

    @Test
    fun `current intent with fewer than 3 meaningful tokens always allows`() {
        // "I want to" → all stop words, 0 meaningful tokens
        assertFalse(IntentSimilarityChecker.isTooSimilar(
            "I want to",
            listOf("I want to check something very important right now please")
        ))
    }

    @Test
    fun `past intent with fewer than 3 tokens is skipped`() {
        val current = "I want to check if my friend replied about the weekend plans tonight"
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, listOf("hey")))
    }

    @Test
    fun `rejects on any match across multiple past intents`() {
        val current   = "I need to look at my DMs to see if my friend responded"
        val unrelated = "research pasta carbonara recipe cook dinner tonight home"
        val similar   = "I want to check my messages to see if my buddy replied"
        // First entry doesn't match — second does — should still reject
        assertFalse(IntentSimilarityChecker.isTooSimilar(current, listOf(unrelated)))
        assertTrue (IntentSimilarityChecker.isTooSimilar(current, listOf(unrelated, similar)))
    }

    @Test
    fun `extra whitespace and punctuation are handled gracefully`() {
        val current = "I want to  check if my friend replied,  about the plans!"
        val past    = "I want to check if my friend replied about the plans"
        assertTrue(IntentSimilarityChecker.isTooSimilar(current, listOf(past)))
    }
}
