package com.volxsy.vastypr.translation

import com.volxsy.vastypr.data.security.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// Skill: android-networking-retrofit-okhttp + android-security-best-practices
// Kontrak tunggal; key diambil dari ApiKeyStore (terenkripsi), tidak pernah hardcode.
interface TranslationProvider {
    val id: String // "agnes" | "gemini" | "sumopod"
    suspend fun translate(text: String, targetLang: String = "id"): String
}

private val JSON = "application/json".toMediaType()
private fun http() = OkHttpClient()

class AgnesTranslator @Inject constructor(private val keys: ApiKeyStore) : TranslationProvider {
    override val id = "agnes"
    override suspend fun translate(text: String, targetLang: String): String =
        withContext(Dispatchers.IO) {
            val key = keys.getAgnes()
            require(key.isNotBlank()) { "Agnes AI key kosong — isi di Settings" }
            // TODO: sesuaikan endpoint resmi Agnes saat dokumentasi dikunci user.
            val body = """{"text":"${text.replace("\"", "\\\"")}","target":"$targetLang"}"""
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("https://api.agnes.ai/v1/translate")
                .addHeader("Authorization", "Bearer $key")
                .post(body)
                .build()
            http().newCall(req).execute().use {
                require(it.isSuccessful) { "Agnes HTTP ${it.code}" }
                TranslationParsers.generic(it.body?.string() ?: text)
            }
        }
}

class GeminiTranslator @Inject constructor(private val keys: ApiKeyStore) : TranslationProvider {
    override val id = "gemini"
    override suspend fun translate(text: String, targetLang: String): String =
        withContext(Dispatchers.IO) {
            val key = keys.getGemini()
            require(key.isNotBlank()) { "Gemini AI key kosong — isi di Settings" }
            val body = """{"contents":[{"parts":[{"text":"Translate to $targetLang: $text"}]}]}"""
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
                .post(body)
                .build()
            http().newCall(req).execute().use {
                require(it.isSuccessful) { "Gemini HTTP ${it.code}" }
                TranslationParsers.gemini(it.body?.string() ?: text)
            }
        }
}

class SumopodTranslator @Inject constructor(private val keys: ApiKeyStore) : TranslationProvider {
    override val id = "sumopod"
    override suspend fun translate(text: String, targetLang: String): String =
        withContext(Dispatchers.IO) {
            val key = keys.getSumopod()
            require(key.isNotBlank()) { "Sumopod key kosong — isi di Settings" }
            val body = """{"text":"${text.replace("\"", "\\\"")}","target":"$targetLang"}"""
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("https://api.sumopod.com/v1/translate")
                .addHeader("Authorization", "Bearer $key")
                .post(body)
                .build()
            http().newCall(req).execute().use {
                require(it.isSuccessful) { "Sumopod HTTP ${it.code}" }
                TranslationParsers.generic(it.body?.string() ?: text)
            }
        }
}
