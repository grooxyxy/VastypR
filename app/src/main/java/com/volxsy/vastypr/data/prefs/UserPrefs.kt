package com.volxsy.vastypr.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Skill: android-local-persistence-datastore — schema-safe, default eksplisit, migrasi-aware.
class UserPrefs(private val store: DataStore<Preferences>) {
    private object Keys {
        val TEXT_STYLE_JSON = stringPreferencesKey("text_style_json")
        // Style manager ala TypeR: map nama → style JSON (1 objek JSON).
        val TEXT_STYLES_JSON = stringPreferencesKey("text_styles_json")
        val EXPORT_FORMAT = stringPreferencesKey("export_format") // jpeg/png/webp
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        // Model manager — skill: android-local-persistence-datastore
        // Backend inpaint aktif + URL download per model. URL kosong = pakai
        // defaultUrl katalog (tautan resmi) — user tetap bisa menempel link
        // GitHub Release sendiri. Tidak ada download otomatis.
        val INPAINT_BACKEND = stringPreferencesKey("inpaint_backend") // telea | lama | migan
        val LAMA_URL = stringPreferencesKey("lama_url")
        val MIGAN_URL = stringPreferencesKey("migan_url")
        val BUBBLE_URL = stringPreferencesKey("bubble_url")
        val PPOCR_URL = stringPreferencesKey("ppocr_url")
    }

    val exportFormat: Flow<String> = store.data.map { it[Keys.EXPORT_FORMAT] ?: "png" }

    suspend fun setExportFormat(v: String) { store.edit { it[Keys.EXPORT_FORMAT] = v } }
    suspend fun saveTextStyleJson(json: String) { store.edit { it[Keys.TEXT_STYLE_JSON] = json } }
    fun textStyleJson(): Flow<String?> = store.data.map { it[Keys.TEXT_STYLE_JSON] }
    suspend fun saveTextStylesJson(json: String) { store.edit { it[Keys.TEXT_STYLES_JSON] = json } }
    fun textStylesJson(): Flow<String?> = store.data.map { it[Keys.TEXT_STYLES_JSON] }

    val inpaintBackend: Flow<String> = store.data.map { it[Keys.INPAINT_BACKEND] ?: "telea" }
    suspend fun setInpaintBackend(v: String) { store.edit { it[Keys.INPAINT_BACKEND] = v } }
    fun lamaUrl(): Flow<String> = store.data.map { it[Keys.LAMA_URL] ?: "" }
    fun miganUrl(): Flow<String> = store.data.map { it[Keys.MIGAN_URL] ?: "" }
    suspend fun setLamaUrl(v: String) { store.edit { it[Keys.LAMA_URL] = v } }
    suspend fun setMiganUrl(v: String) { store.edit { it[Keys.MIGAN_URL] = v } }
    fun bubbleUrl(): Flow<String> = store.data.map { it[Keys.BUBBLE_URL] ?: "" }
    fun ppocrUrl(): Flow<String> = store.data.map { it[Keys.PPOCR_URL] ?: "" }
    suspend fun setBubbleUrl(v: String) { store.edit { it[Keys.BUBBLE_URL] = v } }
    suspend fun setPpocrUrl(v: String) { store.edit { it[Keys.PPOCR_URL] = v } }
}
