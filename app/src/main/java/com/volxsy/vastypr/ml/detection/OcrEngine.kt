package com.volxsy.vastypr.ml.detection

import android.graphics.Bitmap
import android.graphics.PointF

// Skill: android-architecture-clean — OCR ganda sesuai syarat:
// - ML Kit v2 (cepat offline di HP, ringan -> aktif duluan, Latin)
// - PP-OCRv6-small ONNX (akurat untuk font stilasi, model manual user)
// Kontrak memakai Bitmap (caller mengatur sampling). Bila [region] diisi
// (polygon px dalam koordinat [image]), engine memproses crop tersebut namun
// WAJIB mengembalikan polygon dalam koordinat bitmap asal.
data class OcrLine(
    val text: String,
    val polygon: List<PointF>, // bentuk teks, bukan sekadar kotak -> untuk mask presisi
    val score: Float,
)

/** Hasil OCR + ukuran bitmap input (acuan koordinat polygon). */
data class OcrResult(val lines: List<OcrLine>, val width: Int, val height: Int)

interface OcrEngine {
    suspend fun recognize(image: Bitmap, region: List<PointF>? = null): OcrResult
}

class NoopOcrEngine : OcrEngine {
    override suspend fun recognize(image: Bitmap, region: List<PointF>?): OcrResult =
        OcrResult(emptyList(), image.width, image.height)
}

// TODO(PP-OCR): PpOcrV6Engine — ONNX det (DB) + rec (SVTR) small,
// file manual: filesDir/models/ppocr_det.onnx + ppocr_rec.onnx
