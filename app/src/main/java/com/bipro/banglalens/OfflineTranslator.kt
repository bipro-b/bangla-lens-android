package com.bipro.banglalens

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

object OfflineTranslator {

    private val enBn by lazy { client(TranslateLanguage.ENGLISH, TranslateLanguage.BENGALI) }
    private val bnEn by lazy { client(TranslateLanguage.BENGALI, TranslateLanguage.ENGLISH) }
    private val bengaliModel = TranslateRemoteModel.Builder(TranslateLanguage.BENGALI).build()

    private fun client(src: String, tgt: String) = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
    )

    /** Fire-and-forget on Wi-Fi so the fallback is ready before it's ever needed. */
    fun prewarm() {
        enBn.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
    }

    /** Blocking; call off the main thread. */
    fun isReady(): Boolean =
        runCatching {
            Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(bengaliModel), 5, TimeUnit.SECONDS)
        }.getOrDefault(false)

    /** Blocking; call off the main thread. Downloads on any network if the model is missing. */
    fun translate(text: String, target: String): String {
        val client = if (target == "bn") enBn else bnEn
        Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()), 3, TimeUnit.MINUTES)
        return Tasks.await(client.translate(text), 60, TimeUnit.SECONDS)
    }
}
