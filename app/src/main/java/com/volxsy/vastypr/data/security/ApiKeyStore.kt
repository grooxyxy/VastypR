package com.volxsy.vastypr.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Skill: android-security-best-practices — API keys di EncryptedSharedPreferences,
// TIDAK di strings.xml / BuildConfig / log, TIDAK ikut backup (lihat backup_rules.xml).
@Singleton
class ApiKeyStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, "secret_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAgnes(): String = prefs.getString("agnes", "") ?: ""
    fun getGemini(): String = prefs.getString("gemini", "") ?: ""
    fun getSumopod(): String = prefs.getString("sumopod", "") ?: ""

    fun setAgnes(v: String) = prefs.edit().putString("agnes", v).apply()
    fun setGemini(v: String) = prefs.edit().putString("gemini", v).apply()
    fun setSumopod(v: String) = prefs.edit().putString("sumopod", v).apply()
}
