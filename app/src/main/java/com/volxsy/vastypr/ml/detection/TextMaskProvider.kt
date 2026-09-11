package com.volxsy.vastypr.ml.detection

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

// Skill: android-architecture-clean — mask teks presisi (bukan OCR baca tulis).
// Di Manhwa-Translator, PP-OCR det dipakai KHUSUS untuk mask inpaint
// (local_text_mask.py); membacanya tetap via vision-LLM. Di VastypR peran yang
// sama: mask otomatis untuk inpaint bila user tak membuat seleksi manual.
// Membaca teks tetap tugas OcrEngine (ML Kit).
data class MaskResult(
    val mask: Bitmap, // ALPHA_8 0/255 dalam koordinat gambar input
    val boxes: List<RectF>, // bbox komponen dalam koordinat gambar input (px)
)

interface TextMaskProvider {
    suspend fun maskFor(image: Bitmap, region: List<PointF>? = null): MaskResult
}
