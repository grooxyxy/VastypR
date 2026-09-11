package com.volxsy.vastypr.data.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

// Skill: android-architecture-clean + android-compose-performance
// Font custom: import TTF/OTF via SAF → filesDir/fonts → preview cepat via LruCache.
// Built-in: default/sans/serif/mono (tanpa file, 0ms).
@Singleton
class FontManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    data class FontInfo(
        val id: String, // "system_default" | "sans" | "serif" | "mono" | nama file
        val displayName: String,
        val isImported: Boolean,
        val sizeKb: Long = 0,
    )

    companion object {
        const val SYSTEM_DEFAULT = "system_default"
        const val PREVIEW_TEXT = "Jangan pergi! ABC 123"
    }

    private val cache = LruCache<String, Typeface>(8) // fast load: max 8 typeface di RAM

    private val _fonts = MutableStateFlow<List<FontInfo>>(builtins())
    val fonts: StateFlow<List<FontInfo>> = _fonts.asStateFlow()

    fun fontsDir(): File = File(ctx.filesDir, "fonts").apply { mkdirs() }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val imported = fontsDir().listFiles()
            ?.filter { it.isFile && (it.extension.lowercase() in setOf("ttf", "otf")) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { FontInfo(it.name, it.nameWithoutExtension, true, it.length() / 1024) }
            ?: emptyList()
        _fonts.update { builtins() + imported }
        // Preload ke cache agar preview & canvas instan.
        imported.take(8).forEach { getTypeface(it.id) }
    }

    private fun builtins() = listOf(
        FontInfo(SYSTEM_DEFAULT, "System Default", false),
        FontInfo("sans", "Sans", false),
        FontInfo("serif", "Serif", false),
        FontInfo("mono", "Mono", false),
    )

    /** Import font dari SAF uri. Return pesan hasil (tidak melempar). */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = (uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "font_${System.currentTimeMillis()}.ttf")
            .let { if (it.contains('.')) it else "$it.ttf" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!name.lowercase().endsWith(".ttf") && !name.lowercase().endsWith(".otf")) {
            return@withContext "Bukan font (harus .ttf/.otf)"
        }
        val dest = File(fontsDir(), name)
        if (dest.exists()) return@withContext "Font $name sudah ada"
        try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                dest.outputStream().use { ins.copyTo(it) }
            } ?: return@withContext "Gagal membuka file"
            // Validasi bisa di-parse sebagai typeface.
            runCatching { Typeface.createFromFile(dest) }.onFailure {
                dest.delete()
                return@withContext "File rusak / bukan font valid"
            }
            refresh()
            "Font $name diimport"
        } catch (e: Exception) {
            dest.delete()
            "Import gagal: ${e.message}"
        }
    }

    suspend fun delete(id: String): String = withContext(Dispatchers.IO) {
        if (!id.endsWith(".ttf", true) && !id.endsWith(".otf", true)) return@withContext "Font sistem tidak bisa dihapus"
        cache.remove(id)
        if (File(fontsDir(), id).delete()) { refresh(); "Font $id dihapus" } else "Gagal menghapus"
    }

    /** Sinkron + cached. Aman dipanggil dari composition (cache hit setelah preload). */
    fun getTypeface(id: String?): Typeface? {
        if (id == null || id == SYSTEM_DEFAULT) return null // null = Compose Default
        cache.get(id)?.let { return it }
        val tf = when (id) {
            "sans" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            else -> runCatching { Typeface.createFromFile(File(fontsDir(), id)) }.getOrNull()
        } ?: return null
        cache.put(id, tf)
        return tf
    }
}
