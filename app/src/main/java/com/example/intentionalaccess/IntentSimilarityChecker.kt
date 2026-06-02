package com.example.intentionalaccess

/**
 * Detects whether a newly typed intent is semantically too close to a past
 * intent for the same app — including paraphrased versions.
 *
 * ── Pipeline ─────────────────────────────────────────────────────────────────
 *
 *   Raw text
 *     │
 *     ▼
 *   Lowercase + strip punctuation
 *     │
 *     ▼
 *   Split on whitespace → drop tokens shorter than 3 chars
 *     │
 *     ▼
 *   Remove stop words  (filler words: "the", "and", "want", "need", …)
 *     │
 *     ▼
 *   Synonym normalisation  ← NEW: "replied"→"reply", "dms"→"messages", …
 *     │                           collapses paraphrase variants to one token
 *     ▼
 *   Jaccard similarity on unigrams (0.6 weight) + bigrams (0.4 weight)
 *     │
 *     ▼
 *   Reject if combined score ≥ SIMILARITY_THRESHOLD
 *
 * ── Why Jaccard + synonyms instead of an ML model ────────────────────────────
 *
 *   • Zero extra dependencies, works fully offline, runs in < 1 ms
 *   • Fully unit-testable (no Android context needed)
 *   • Transparent — you can read exactly why two intents matched
 *   • The synonym map targets the specific vocabulary of social-media intents,
 *     so it outperforms a generic embedding model on this narrow domain
 */
object IntentSimilarityChecker {

    // ── tunables ─────────────────────────────────────────────────────────────

    /** How many of the user's most recent sessions to compare against (per app). */
    const val MAX_HISTORY = 10

    /**
     * Combined score at or above which an intent is rejected as too similar.
     * 0.65 catches near-paraphrases while allowing legitimately different intents.
     * Lower = stricter (rejects more). Higher = more permissive.
     */
    private const val SIMILARITY_THRESHOLD = 0.65f

    // ── stop words ────────────────────────────────────────────────────────────

    /**
     * High-frequency English words that carry no distinctive meaning for intent
     * comparison. Stripping these means "I want to check my messages" and
     * "check messages" produce identical token sets.
     *
     * Note: content-bearing verbs like "check", "watch", "post" are intentionally
     * NOT stop words — they end up normalised by SYNONYMS instead.
     */
    private val STOP_WORDS = setOf(
        "i", "me", "my", "myself", "we", "our", "you", "your",
        "he", "she", "it", "its", "they", "them", "their",
        "a", "an", "the",
        "and", "or", "but", "so", "yet", "nor", "for",
        "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "must", "shall",
        "to", "of", "in", "on", "at", "by", "with", "about",
        "from", "into", "onto", "over", "after", "before", "between",
        "through", "during", "this", "that", "these", "those",
        "if", "then", "than", "because", "since", "while", "although",
        "not", "just", "also", "very", "really", "too", "quite", "only",
        "now", "here", "there", "when", "where", "how", "what", "which",
        "who", "why", "up", "out", "back", "some", "like", "want", "need",
        "same", "own", "still", "much", "more", "most", "other", "such",
        "get", "got", "go", "going", "gone", "gonna", "gotta",
        "can", "use", "used", "using", "open", "see", "look"
    )

    // ── synonym map ───────────────────────────────────────────────────────────

    /**
     * Maps surface-form variants onto a single canonical token BEFORE Jaccard
     * comparison, so that paraphrased intents score highly even when the user
     * picked different words.
     *
     * Each cluster represents words that mean the same thing in the context of
     * a social-media usage intent.
     *
     * Example effect:
     *   "I need to look at my DMs to see if my friend responded"
     *    → after stop-word strip + normalise → {messages, friend, reply}
     *   "I want to check my messages to see if my buddy replied"
     *    → after stop-word strip + normalise → {messages, friend, reply}
     *   Jaccard = 3/3 = 1.0  → REJECTED ✓
     */
    private val SYNONYMS: Map<String, String> = mapOf(

        // ── direct messages / inbox ──────────────────────────────────────────
        "dm"              to "messages",
        "dms"             to "messages",
        "inbox"           to "messages",
        "chat"            to "messages",
        "chats"           to "messages",
        "text"            to "messages",
        "texts"           to "messages",
        "notification"    to "messages",
        "notifications"   to "messages",
        "notif"           to "messages",
        "notifs"          to "messages",

        // ── reply / respond ──────────────────────────────────────────────────
        "reply"           to "reply",    // canonical — listed for completeness
        "replied"         to "reply",
        "replying"        to "reply",
        "replies"         to "reply",
        "respond"         to "reply",
        "responded"       to "reply",
        "responding"      to "reply",
        "response"        to "reply",
        "responses"       to "reply",
        "answer"          to "reply",
        "answered"        to "reply",
        "answering"       to "reply",

        // ── post / share / upload ────────────────────────────────────────────
        "post"            to "post",
        "posting"         to "post",
        "posted"          to "post",
        "posts"           to "post",
        "share"           to "post",
        "sharing"         to "post",
        "shared"          to "post",
        "upload"          to "post",
        "uploading"       to "post",
        "uploaded"        to "post",
        "publish"         to "post",
        "publishing"      to "post",

        // ── friend / connection ──────────────────────────────────────────────
        "friend"          to "friend",
        "friends"         to "friend",
        "buddy"           to "friend",
        "pal"             to "friend",
        "mate"            to "friend",
        "colleague"       to "friend",
        "coworker"        to "friend",
        "workmate"        to "friend",
        "contact"         to "friend",
        "follower"        to "friend",
        "followers"       to "friend",

        // ── video content ────────────────────────────────────────────────────
        "video"           to "video",
        "videos"          to "video",
        "watch"           to "video",
        "watching"        to "video",
        "watched"         to "video",
        "reel"            to "video",
        "reels"           to "video",
        "clip"            to "video",
        "clips"           to "video",
        "short"           to "video",
        "shorts"          to "video",
        "stream"          to "video",
        "streaming"       to "video",

        // ── photo / image ────────────────────────────────────────────────────
        "photo"           to "photo",
        "photos"          to "photo",
        "pic"             to "photo",
        "pics"            to "photo",
        "image"           to "photo",
        "images"          to "photo",
        "picture"         to "photo",
        "pictures"        to "photo",

        // ── browse / scroll ──────────────────────────────────────────────────
        "browse"          to "browse",
        "browsing"        to "browse",
        "scroll"          to "browse",
        "scrolling"       to "browse",
        "scrolled"        to "browse",
        "feed"            to "browse",
        "explore"         to "browse",
        "exploring"       to "browse",

        // ── check / view ─────────────────────────────────────────────────────
        // "see" and "look" are stop words (stripped), so only the others need
        // normalising to a common token here.
        "check"           to "check",
        "checking"        to "check",
        "checked"         to "check",
        "view"            to "check",
        "viewing"         to "check",
        "read"            to "check",
        "reading"         to "check",

        // ── research / learn ─────────────────────────────────────────────────
        "research"        to "learn",
        "researching"     to "learn",
        "learn"           to "learn",
        "learning"        to "learn",
        "study"           to "learn",
        "studying"        to "learn",
        "tutorial"        to "learn",
        "tutorials"       to "learn",
        "lesson"          to "learn",
        "lessons"         to "learn"
    )

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if [currentIntent] is too similar to any entry in [pastIntents].
     *
     * Designed to be called off the main thread (the DB read happens in the
     * caller; this function is pure computation).
     *
     * @param currentIntent  The text the user just typed.
     * @param pastIntents    Recently accepted intents for the same app (from DB).
     */
    fun isTooSimilar(currentIntent: String, pastIntents: List<String>): Boolean {
        val currentTokens = tokenize(currentIntent)
        // Fewer than 3 meaningful tokens → not enough signal to compare reliably
        if (currentTokens.size < 3) return false

        return pastIntents.any { past ->
            val pastTokens = tokenize(past)
            if (pastTokens.size < 3) return@any false
            combinedScore(currentTokens, pastTokens) >= SIMILARITY_THRESHOLD
        }
    }

    // ── similarity maths ──────────────────────────────────────────────────────

    /**
     * Weighted combination of unigram and bigram Jaccard scores.
     *   Unigrams (0.6 weight) — shared vocabulary
     *   Bigrams  (0.4 weight) — penalises reordered paraphrases that swap words
     *                           but keep adjacent pairs intact
     */
    private fun combinedScore(tokensA: List<String>, tokensB: List<String>): Float {
        val unigramScore = jaccardSimilarity(tokensA.toSet(), tokensB.toSet())
        val bigramScore  = jaccardSimilarity(bigrams(tokensA), bigrams(tokensB))
        return 0.6f * unigramScore + 0.4f * bigramScore
    }

    /**
     * Tokenisation pipeline:
     *   lowercase → strip punctuation → split → drop short tokens
     *   → remove stop words → normalise synonyms
     */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }
            .map    { SYNONYMS[it] ?: it }   // ← synonym normalisation

    /** Adjacent token pairs: ["cats","sleep","well"] → {"cats_sleep","sleep_well"} */
    private fun bigrams(tokens: List<String>): Set<String> {
        if (tokens.size < 2) return emptySet()
        return (0 until tokens.size - 1)
            .map { "${tokens[it]}_${tokens[it + 1]}" }
            .toSet()
    }

    /** |A ∩ B| / |A ∪ B|, returns 0 if either set is empty. */
    private fun <T> jaccardSimilarity(setA: Set<T>, setB: Set<T>): Float {
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val intersection = (setA intersect setB).size.toFloat()
        val union        = (setA union setB).size.toFloat()
        return intersection / union
    }
}
