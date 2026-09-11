package com.volxsy.vastypr.ml.models

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Dilempar bila file .onnx yang dibutuhkan belum ada di filesDir/models.
// Prinsip user: model dimasukkan MANUAL (copy file / Settings → Models),
// tidak pernah di-download otomatis oleh app. Pengecualian: bubble detector
// DIBUNDLE di APK saat build (assets/models via task bundleBubbleModel) lalu
// disalin sekali ke filesDir/models saat pertama dipakai (butuh path file
// untuk ORT session).
class MissingModelException(message: String) : IllegalStateException(message)

// Skill: android-architecture-clean — akses file model manual terpusat.
@Singleton
class ModelFiles @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun dir(): File = File(ctx.filesDir, "models").apply { mkdirs() }

    fun file(name: String): File = File(dir(), name)

    fun exists(name: String): Boolean = file(name).let { it.exists() && it.length() > 0 }

    fun require(name: String, label: String): File {
        val f = file(name)
        if (!f.exists() || f.length() <= 0) throw MissingModelException(
            "$label belum ada — download manual via Settings → Models " +
                "(atau copy file $name ke folder models)"
        )
        return f
    }

    /** Coba beberapa nama file berurutan (copy dari Manhwa-Translator bisa beda nama). */
    fun requireFirst(names: List<String>, label: String): File {
        for (n in names) {
            val f = file(n)
            if (f.exists() && f.length() > 0) return f
        }
        throw MissingModelException(
            "$label belum ada — masukkan salah satu file ${names.joinToString("/")} " +
                "manual ke folder models (atau via Settings → Inpaint Models)"
        )
    }

    /**
     * Resolver bubble detector: filesDir dulu, lalu assets bundled.
     * File assets disalin sekali ke filesDir (ORT butuh path file).
     * Dipakai YoloV8mBubbleDetector agar APK bundle langsung jalan.
     */
    fun requireFirstOrBundled(names: List<String>, label: String): File {
        for (n in names) {
            val f = file(n)
            if (f.exists() && f.length() > 0) return f
        }
        for (n in names) {
            copyAssetToFiles("models/$n", n)?.let { return it }
        }
        throw MissingModelException(
            "$label belum ada — APK ini tanpa bundle + file manual belum ada. " +
                "Masukkan salah satu file ${names.joinToString("/")} manual " +
                "ke folder models (atau via Settings → Models → Download)"
        )
    }

    /** Benar bila salah satu nama tersedia di assets/models (APK hasil bundle). */
    fun isBundled(names: List<String>): Boolean = runCatching {
        val assets = ctx.assets.list("models")?.toSet() ?: emptySet()
        names.any { it in assets }
    }.getOrDefault(false)

    /** Salin assets/models/<assetPath> → filesDir/models/<targetName> bila belum ada. */
    private fun copyAssetToFiles(assetPath: String, targetName: String): File? = runCatching {
        ctx.assets.open(assetPath).use { ins ->
            val out = file(targetName)
            if (out.exists() && out.length() > 0) return@runCatching out
            out.outputStream().use { outs -> ins.copyTo(outs) }
            if (out.exists() && out.length() > 0) out else null
        }
    }.getOrNull()
}
