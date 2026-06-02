package com.example.intentionalaccess

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lightweight SQLite store for intent sessions.
 *
 * Replaces the SharedPreferences JSON array approach, which had two problems:
 *   1. The entire session log was one giant JSON string — O(n) parse on every gate open.
 *   2. A corrupt character in any entry could silently destroy all history.
 *
 * Schema (v1):
 *   intent_sessions(id, package_name, intent_text, timestamp)
 *
 * An index on package_name means "give me the last 10 intents for Instagram"
 * is a fast indexed scan, not a full-table walk.
 *
 * Singleton: one helper instance per process, created with applicationContext
 * so it outlives any individual Activity.
 */
class IntentSessionDb private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {

    // ── schema ────────────────────────────────────────────────────────────────

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                $COL_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE   TEXT    NOT NULL,
                $COL_INTENT    TEXT    NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())

        // Index makes per-package queries fast even with thousands of rows
        db.execSQL("CREATE INDEX idx_package ON $TABLE ($COL_PACKAGE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple strategy for v1 → v2+: drop and recreate.
        // Add ALTER TABLE migrations here when the schema grows.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    // ── write ─────────────────────────────────────────────────────────────────

    /**
     * Persists a new intent session.
     * Must be called from a background thread — never the main thread.
     */
    fun insertSession(packageName: String, intentText: String) {
        writableDatabase.insertOrThrow(TABLE, null, ContentValues().apply {
            put(COL_PACKAGE,   packageName)
            put(COL_INTENT,    intentText)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        })
    }

    // ── read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the [limit] most recent intent strings recorded for [packageName],
     * newest first. Used by [IntentSimilarityChecker] to load comparison targets.
     *
     * Must be called from a background thread — never the main thread.
     */
    fun getRecentIntents(packageName: String, limit: Int): List<String> {
        val results = mutableListOf<String>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_INTENT),
            "$COL_PACKAGE = ?",
            arrayOf(packageName),
            null, null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    // ── singleton ─────────────────────────────────────────────────────────────

    companion object {
        private const val DB_NAME    = "intentional_access.db"
        private const val DB_VERSION = 1

        private const val TABLE         = "intent_sessions"
        private const val COL_ID        = "id"
        private const val COL_PACKAGE   = "package_name"
        private const val COL_INTENT    = "intent_text"
        private const val COL_TIMESTAMP = "timestamp"

        @Volatile private var instance: IntentSessionDb? = null

        fun getInstance(context: Context): IntentSessionDb =
            instance ?: synchronized(this) {
                instance ?: IntentSessionDb(context).also { instance = it }
            }
    }
}
