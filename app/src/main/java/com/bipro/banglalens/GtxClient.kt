package com.bipro.banglalens

import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google is throttling this IP (429/403/captcha page). Trips the circuit breaker. */
class ThrottledException(val retryAfterMs: Long?) : IOException("throttled")

object GtxClient {

    fun translate(text: String, target: String): String {
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray()) }
            when (val code = conn.responseCode) {
                200 -> Unit
                429, 403 -> throw ThrottledException(
                    conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000)
                )
                else -> throw IOException("gtx $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val segments = try {
                JSONArray(body).getJSONArray(0)
            } catch (e: JSONException) {
                throw ThrottledException(null) // 200 + HTML captcha = soft block
            }
            return buildString {
                for (i in 0 until segments.length()) {
                    segments.optJSONArray(i)?.let { if (!it.isNull(0)) append(it.getString(0)) }
                }
            }.ifBlank { throw IOException("gtx empty") }
        } finally {
            conn.disconnect()
        }
    }
}
