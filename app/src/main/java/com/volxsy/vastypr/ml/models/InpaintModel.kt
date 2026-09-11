package com.volxsy.vastypr.ml.models

// Skill: android-architecture-clean — katalog model sebagai data murni.
// Prinsip user:
// - Bubble detector DIBUNDLE di APK saat build (via task bundleBubbleModel dari
//   file lokal / URL yang user sediakan — lihat app/build.gradle.kts). Fallback:
//   file manual di filesDir/models seperti model lain.
// - Model lain TIDAK dibundel — download MANUAL eksplisit per tombol di
//   Settings → Models (atau copy file). APK tidak mengunduh apapun sendiri.
// - Telea built-in (tanpa file) = backend default.
enum class InpaintBackend(val id: String) {
    TELEA("telea"), // OpenCV, built-in, ringan — tanpa download
    LAMA("lama"),   // neural, kualitas terbaik, berat — download opsional
    MIGAN("migan"), // GAN ringan (MiGAN), kompromi speed/quality — download opsional
    ;

    companion object {
        fun fromId(id: String): InpaintBackend =
            entries.firstOrNull { it.id == id } ?: TELEA
    }
}

data class DownloadableModel(
    val backend: InpaintBackend,
    val modelId: String, // "bubble" | "ppocr" | "lama" | "migan"
    val label: String,
    val fileName: String, // di filesDir/models/ (dan assets/models/ bila bundled)
    val aliases: List<String> = emptyList(), // nama file lama yang masih diterima
    val estimateMb: String, // estimasi kasar, angka pasti via HEAD saat URL diisi
    val description: String,
    // Tautan awal resmi (bisa diganti user di kolom URL). Kosong = wajib isi manual.
    val defaultUrl: String = "",
    // true = di-bundle ke APK saat build (tidak perlu download bila build menyertakannya).
    val bundled: Boolean = false,
)

// Urutan tampil di Settings → Models.
val MODEL_CATALOG = listOf(
    DownloadableModel(
        backend = InpaintBackend.TELEA, // penanda "bukan backend inpaint"
        modelId = "bubble",
        label = "Bubble Detector (YOLOv8m)",
        fileName = "comic-speech-bubble-detector.onnx",
        aliases = listOf("bubble_yolov8m.onnx"),
        estimateMb = "~99 MB",
        description = "Wajib untuk tombol Bubble & Clean. DIBUNDLE di APK saat build " +
            "(lihat BUBBLE_MODEL_PATH/URL); fallback file manual bila APK tanpa bundle.",
        defaultUrl = "",
        bundled = true,
    ),
    DownloadableModel(
        backend = InpaintBackend.TELEA,
        modelId = "ppocr",
        label = "PP-OCR det (mask teks)",
        fileName = "PP-OCRv6_small_det.onnx",
        estimateMb = "~10 MB",
        description = "Mask teks otomatis presisi untuk Inpaint/Clean tanpa seleksi manual. " +
            "Resmi PaddlePaddle (Apache-2.0).",
        defaultUrl = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx",
    ),
    DownloadableModel(
        backend = InpaintBackend.MIGAN,
        modelId = "migan",
        label = "MiGAN (light)",
        fileName = "migan_lxfater.onnx", // sama seperti migan_inpaint.py Manhwa-Translator
        estimateMb = "~30 MB",
        description = "Ringan & cepat (input 4ch 512, fallback Telea bila gagal). Cocok bubble kecil-menengah. " +
            "Sumber lxfater/inpaint-web (GPL-3.0).",
        defaultUrl = "https://huggingface.co/lxfater/inpaint-web/resolve/main/migan.onnx",
    ),
    DownloadableModel(
        backend = InpaintBackend.LAMA,
        modelId = "lama",
        label = "LaMa (large)",
        fileName = "lama_fp32.onnx",
        estimateMb = "~207 MB",
        description = "Kualitas terbaik untuk background kompleks. Lambat di HP lama, unduh via WiFi. " +
            "Port Carve/LaMa-ONNX (Apache-2.0, varian fp32 RECOMMENDED).",
        defaultUrl = "https://huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx",
    ),
)

// Kompat: layar lama hanya menampilkan backend inpaint.
val INPAINT_CATALOG = MODEL_CATALOG.filter { it.modelId == "lama" || it.modelId == "migan" }

fun modelById(id: String): DownloadableModel? = MODEL_CATALOG.firstOrNull { it.modelId == id }
