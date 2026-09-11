package com.volxsy.vastypr.editor.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Skill: android-media-files-sharing — export via MediaStore (tanpa WRITE permission di API 29+),
// custom resolution + custom name sesuai syarat user. Mendukung jpeg/png/webp (import: all image).
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    enum class Format(val mime: String, val ext: String, val compress: Bitmap.CompressFormat) {
        JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG),
        PNG("image/png", "png", Bitmap.CompressFormat.PNG),
        WEBP("image/webp", "webp", Bitmap.CompressFormat.WEBP),
    }

    data class Request(
        val bitmap: Bitmap,
        val fileName: String, // custom name dari user, tanpa ekstensi
        val format: Format = Format.PNG,
        val targetLongSide: Int? = null, // custom resolution: skala sisi panjang
        val quality: Int = 95,
    )

    suspend fun export(req: Request): Uri = withContext(Dispatchers.IO) {
        val src = req.bitmap
        val bmp = req.targetLongSide?.let { scaleToLongSide(src, it) } ?: src
        val name = req.fileName.ifBlank { "VastypR_${System.currentTimeMillis()}" } + "." + req.format.ext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, req.format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VastypR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out ->
                if (!bmp.compress(req.format.compress, req.quality, out)) {
                    throw IllegalStateException("Compress failed")
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            // API 26-28 fallback: tulis ke Pictures/VastypR + scan.
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "VastypR"
            ).apply { mkdirs() }
            val f = File(dir, name)
            FileOutputStream(f).use { out ->
                if (!bmp.compress(req.format.compress, req.quality, out)) {
                    throw IllegalStateException("Compress failed")
                }
            }
            Uri.fromFile(f)
        }.also {
            if (bmp !== src) bmp.recycle()
        }
    }

    private fun scaleToLongSide(src: Bitmap, longSide: Int): Bitmap {
        val w = src.width; val h = src.height
        val long = maxOf(w, h)
        if (long <= longSide) return src
        val ratio = longSide.toFloat() / long
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
