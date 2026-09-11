package com.volxsy.vastypr.ml.detection

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Skill: android-architecture-clean — OCR cepat offline (Latin).
// Artefak: com.google.mlkit:text-recognition (sudah diaktifkan di gradle).
// CJK (Korea/Jepang/Cina) butuh artefak text-recognition-{korean,japanese,chinese}
// terpisah = dep baru -> DITAHAN sampai user setuju (aturan tanpa dep baru).
// Bila [region] diisi, crop bbox-nya diproses namun polygon hasil dikembalikan
// dalam koordinat bitmap asal (sesuai kontrak OcrEngine).
class MlKitOcrEngine : OcrEngine {

    override suspend fun recognize(image: Bitmap, region: List<PointF>?): OcrResult =
        withContext(Dispatchers.Default) {
            require(image.width > 0 && image.height > 0) { "Bitmap kosong" }
            var ox = 0
            var oy = 0
            var work = image
            var cropped: Bitmap? = null
            if (region != null && region.size >= 3) {
                val l = region.minOf { it.x }.toInt().coerceIn(0, image.width - 1)
                val t = region.minOf { it.y }.toInt().coerceIn(0, image.height - 1)
                val r = region.maxOf { it.x }.toInt().coerceIn(l + 1, image.width)
                val b = region.maxOf { it.y }.toInt().coerceIn(t + 1, image.height)
                if (r - l >= 8 && b - t >= 8) {
                    cropped = Bitmap.createBitmap(image, l, t, r - l, b - t)
                    ox = l
                    oy = t
                    work = cropped
                }
            }
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val result = suspendCancellableCoroutine<Text> { cont ->
                        recognizer.process(InputImage.fromBitmap(work, 0))
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                            .addOnCanceledListener { cont.cancel() }
                    }
                    val lines = result.textBlocks.flatMap { block ->
                        if (block.text.isBlank()) return@flatMap emptyList()
                        val pts: List<PointF> = block.cornerPoints
                            ?.map { PointF((it.x + ox).toFloat(), (it.y + oy).toFloat()) }
                            ?: block.boundingBox?.let { bb ->
                                listOf(
                                    PointF((bb.left + ox).toFloat(), (bb.top + oy).toFloat()),
                                    PointF((bb.right + ox).toFloat(), (bb.top + oy).toFloat()),
                                    PointF((bb.right + ox).toFloat(), (bb.bottom + oy).toFloat()),
                                    PointF((bb.left + ox).toFloat(), (bb.bottom + oy).toFloat()),
                                )
                            }
                            ?: listOf(
                                PointF(ox.toFloat(), oy.toFloat()),
                                PointF((ox + work.width).toFloat(), oy.toFloat()),
                                PointF((ox + work.width).toFloat(), (oy + work.height).toFloat()),
                                PointF(ox.toFloat(), (oy + work.height).toFloat()),
                            )
                        listOf(OcrLine(block.text, pts, 1f))
                    }
                    OcrResult(lines, image.width, image.height)
                } finally {
                    runCatching { recognizer.close() }
                }
            } finally {
                if (cropped != null && !cropped.isRecycled) cropped.recycle()
            }
        }
}
