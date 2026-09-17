package com.bipro.banglalens

import android.content.Context
import kotlin.math.max
import kotlin.math.min

enum class Route { ONLINE, OFFLINE_BUDGET, OFFLINE_COOLDOWN }

/**
 * Keeps this device under Google's radar so we switch to ML Kit *before* a 429.
 *
 * 1. Sliding-window budget: > MAX_WORDS words or > MAX_HITS requests in WINDOW_MS → offline.
 * 2. Circuit breaker: carrier CGNAT means other people's traffic can still get our IP
 *    throttled. On a throttle we stay offline with exponential backoff (1m → 30m).
 *
 * Persisted in SharedPreferences: the activity is short-lived and the process gets killed.
 */
class UsageGuard(context: Context) {

    companion object {
        const val WINDOW_MS = 5 * 60_000L
        const val MAX_WORDS = 500
        const val MAX_HITS = 40
        const val BASE_COOLDOWN_MS = 60_000L
        const val MAX_COOLDOWN_MS = 30 * 60_000L

        private const val K_HITS = "hits"          // "ts:words;ts:words"
        private const val K_OPEN_UNTIL = "open_until"
        private const val K_FAILS = "fails"
    }

    private val prefs = context.getSharedPreferences("usage_guard", Context.MODE_PRIVATE)

    @Synchronized
    fun decide(words: Int, now: Long = System.currentTimeMillis()): Route {
        if (now < prefs.getLong(K_OPEN_UNTIL, 0)) return Route.OFFLINE_COOLDOWN
        val hits = window(now)
        return if (hits.size >= MAX_HITS || hits.sumOf { it.second } + words > MAX_WORDS) {
            Route.OFFLINE_BUDGET
        } else {
            Route.ONLINE
        }
    }

    /** Count the attempt before sending: Google counts it whether or not it succeeds. */
    @Synchronized
    fun reserve(words: Int, now: Long = System.currentTimeMillis()) {
        val hits = window(now) + (now to words)
        prefs.edit().putString(K_HITS, hits.joinToString(";") { "${it.first}:${it.second}" }).apply()
    }

    @Synchronized
    fun recordSuccess() {
        if (prefs.getInt(K_FAILS, 0) != 0) prefs.edit().putInt(K_FAILS, 0).apply()
    }

    @Synchronized
    fun recordThrottle(retryAfterMs: Long?, now: Long = System.currentTimeMillis()) {
        val fails = prefs.getInt(K_FAILS, 0) + 1
        val backoff = min(BASE_COOLDOWN_MS shl min(fails - 1, 10), MAX_COOLDOWN_MS)
        prefs.edit()
            .putInt(K_FAILS, fails)
            .putLong(K_OPEN_UNTIL, now + max(backoff, retryAfterMs ?: 0))
            .apply()
    }

    private fun window(now: Long): List<Pair<Long, Int>> =
        prefs.getString(K_HITS, "").orEmpty()
            .split(';')
            .mapNotNull { e ->
                val parts = e.split(':')
                val ts = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val w = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                (ts to w).takeIf { now - ts < WINDOW_MS }
            }
}
