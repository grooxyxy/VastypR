package com.volxsy.vastypr.ml.detection

import android.graphics.Bitmap
import android.graphics.RectF

// Skill: android-architecture-clean — kontrak murni, implementasi ONNX di MVP-2.
// Model: yolov8m milik user (format .onnx, dimasukkan MANUAL ke filesDir/models
// atau via Settings → Models) — TIDAK dibundel di APK, TIDAK di-download otomatis.
// Kontrak memakai Bitmap agar caller (VM) yang mengatur sampling anti-OOM;
// koordinat box dikembalikan dalam px bitmap yang diberikan.
data class Bubble(val box: RectF, val score: Float)

/** Hasil deteksi + ukuran bitmap input (acuan koordinat box). */
data class BubbleResult(val bubbles: List<Bubble>, val width: Int, val height: Int)

interface BubbleDetector {
    suspend fun detect(image: Bitmap): BubbleResult
}

/** Fallback bila model belum ada — melempar MissingModelException yang ramah. */
class NoopBubbleDetector : BubbleDetector {
    override suspend fun detect(image: Bitmap): BubbleResult = BubbleResult(
        emptyList(), image.width, image.height
    )
}
