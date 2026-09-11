package com.volxsy.vastypr.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

// Skill: android-compose-performance + android-performance-observability
// Gambar 720x16000+ TIDAK boleh di-load full (puluhan MP -> OOM di Android 8).
// Strategi MVP-1:
//  - probesize via inJustDecodeBounds
//  - render viewport via BitmapRegionDecoder.decodeRegion (tile)
//  - thumbnail kecil via inSampleSize untuk overview/layer list
// MVP-2: tambah disk cache tile + prefetch saat pan cepat.
@Singleton
class TallImageManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    data class ImageInfo(val width: Int, val height: Int, val mime: String?)

    suspend fun probe(uri: Uri): ImageInfo = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri).use { ins ->
            requireNotNull(ins) { "Cannot open $uri" }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(ins, null, opts)
            ImageInfo(opts.outWidth, opts.outHeight, opts.outMimeType)
        }
    }

    /**
     * Decode 1 tile [left,top,right,bottom) dengan downsample opsional.
     * Dipanggil dari Canvas viewport — jangan di Main thread.
     */
    suspend fun decodeRegion(uri: Uri, left: Int, top: Int, right: Int, bottom: Int, sampleSize: Int = 1) =
        withContext(Dispatchers.IO) {
            var input: InputStream? = null
            var decoder: BitmapRegionDecoder? = null
            try {
                input = ctx.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open $uri")
                decoder = BitmapRegionDecoder.newInstance(input, false)
                    ?: throw IllegalStateException("RegionDecoder unsupported")
                val w = decoder.width
                val h = decoder.height
                val rect = android.graphics.Rect(
                    left.coerceIn(0, w - 1),
                    top.coerceIn(0, h - 1),
                    right.coerceIn(1, w),
                    bottom.coerceIn(1, h),
                )
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
                decoder.decodeRegion(rect, opts)
            } finally {
                try { decoder?.recycle() } catch (_: Exception) {}
                try { input?.close() } catch (_: Exception) {}
            }
        }

    /**
     * Decode seluruh gambar dengan sisi panjang dibatasi [maxLongSide] (power-of-2
     * sample + scale akhir bila perlu). Aman untuk tall-image; dipakai ML + export.
     * Melempar bila Uri tak bisa dibaca.
     */
    suspend fun decodeSampled(uri: Uri, maxLongSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            BitmapFactory.decodeStream(ins, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot probe $uri" }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxLongSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp: Bitmap? = null
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            bmp = BitmapFactory.decodeStream(ins, null, opts)
        }
        var out = requireNotNull(bmp) { "Cannot decode $uri" }
        val longSide = maxOf(out.width, out.height)
        if (longSide > maxLongSide) {
            val ratio = maxLongSide.toFloat() / longSide
            val scaled = Bitmap.createScaledBitmap(
                out, (out.width * ratio).toInt(), (out.height * ratio).toInt(), true
            )
            if (scaled !== out) out.recycle()
            out = scaled
        }
        out
    }
}
