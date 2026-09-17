package com.bipro.banglalens

object TextTools {

    /** "bn" if the text is mostly Latin, "en" if mostly Bengali script. */
    fun targetFor(text: String): String {
        val bangla = text.count { it in '\u0980'..'\u09FF' }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return if (bangla > latin) "en" else "bn"
    }

    /** Fixes PDF copy artefacts: "mechan-\nism" -> "mechanism", hard wraps -> spaces. */
    fun clean(s: String): String = s
        .replace(Regex("(\\p{L})-\\r?\\n\\s*(\\p{Ll})"), "\$1\$2")
        .replace(Regex("\\s*\\r?\\n\\s*"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()

    fun wordCount(s: String): Int = s.split(Regex("\\s+")).count { it.isNotBlank() }
}
