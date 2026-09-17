package com.bipro.banglalens

import android.content.Context
import java.io.IOException

enum class Engine { ONLINE, OFFLINE }

data class TranslationResult(val text: String, val engine: Engine)

class TranslationRouter private constructor(context: Context) {

    companion object {
        @Volatile private var instance: TranslationRouter? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: TranslationRouter(context.applicationContext).also { instance = it }
        }
    }

    private val guard = UsageGuard(context)

    // Only online results are cached: they're better quality and cache hits cost no budget.
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 200
    }

    /** Blocking; call off the main thread. [onDownloading] fires if the offline model must be fetched. */
    fun translate(text: String, target: String, onDownloading: () -> Unit): TranslationResult {
        val key = "$target|$text"
        synchronized(cache) { cache[key] }?.let { return TranslationResult(it, Engine.ONLINE) }

        val words = TextTools.wordCount(text)
        if (guard.decide(words) == Route.ONLINE) {
            guard.reserve(words)
            try {
                val out = GtxClient.translate(text, target)
                guard.recordSuccess()
                synchronized(cache) { cache[key] = out }
                return TranslationResult(out, Engine.ONLINE)
            } catch (e: ThrottledException) {
                guard.recordThrottle(e.retryAfterMs)
            } catch (e: IOException) {
                // Timeout / no internet: not Google's throttle, so don't trip the breaker.
            }
        }

        if (!OfflineTranslator.isReady()) onDownloading()
        return TranslationResult(OfflineTranslator.translate(text, target), Engine.OFFLINE)
    }
}
