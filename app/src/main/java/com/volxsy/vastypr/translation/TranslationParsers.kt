package com.volxsy.vastypr.translation

import org.json.JSONObject

// Skill: android-networking-retrofit-okhttp + android-kotlin-core
// Parsing respons translate memakai org.json (built-in Android, tanpa dep baru).
// Skema tiap provider bisa berubah — semua parser defensif: gagal parse =
// kembalikan body mentah (dipotong) agar user tetap melihat respons API.
object TranslationParsers {

    /** Gemini generateContent: candidates[0].content.parts[].text digabung. */
    fun gemini(body: String): String {
        runCatching {
            val parts = JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            val out = sb.toString().trim()
            if (out.isNotEmpty()) return out
        }
        return body.take(2000)
    }

    /** Agnes/Sumopod/generik: coba kunci umum, lalu data.*, lalu mentah. */
    fun generic(body: String): String {
        runCatching {
            val root = JSONObject(body)
            val keys = listOf("translatedText", "translation", "result", "text", "target_text", "output")
            keys.forEach { k ->
                val v = root.optString(k, "")
                if (v.isNotBlank()) return v
            }
            val data = root.optJSONObject("data")
            if (data != null) {
                keys.forEach { k ->
                    val v = data.optString(k, "")
                    if (v.isNotBlank()) return v
                }
            }
        }
        return body.take(2000)
    }
}
