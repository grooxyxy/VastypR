package com.volxsy.vastypr.ml.inpaint

import android.graphics.Bitmap

// Skill: android-architecture-clean
// - Telea-lite (DiffusionInpainter, murni Kotlin TANPA OpenCV): cepat + ringan,
//   juara untuk mask kecil di Android 8. Backend default "telea". OpenCV sengaja
//   tidak dipakai (modul ~100MB+, bertentangan dengan prinsip tanpa download besar).
// - LaMa neural (OnnxInpaintRunner 2-input, model manual `lama_fp32.onnx`).
// - MiGAN (MiganInpainter, port persis migan_inpaint.py: input 4ch 512,
//   model manual `migan_lxfater.onnx`, fallback Telea bila inferensi gagal).
interface Inpainter {
    suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap
}

class NoopInpainter : Inpainter {
    override suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap = source
}
