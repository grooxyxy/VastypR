// Skill: android-gradle-build-logic + android-modularization + android-compose-performance
// Modul tunggal :app untuk MVP-1 agar mudah di-build di GitHub.
// Split ke :core/:feature saat codebase > ~30 file (lihat AGENTS.md handoff).
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.volxsy.vastypr"
    compileSdk = 34

    defaultConfig {
        // applicationId lowercase (Play Store convention).
        // User request: com.volxsy.VastypR -> dinormalisasi ke lowercase.
        applicationId = "com.volxsy.vastypr"
        minSdk = 26 // Android 8.0 — syarat user
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-mvp1"

        vectorDrawables { useSupportLibrary = true }

        // Tall image support: largeHeap membantu decode region 720x16000 tanpa OOM.
        // Tetap wajib tiling via BitmapRegionDecoder (lihat TallImageManager).
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Desugaring untuk API 26: java.time, streams, dsb.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { }

    // ---- Model bundle (khusus bubble detector — lainnya TIDAK dibundle) ----
    // Task bundleBubbleModel menyalin file .onnx bubble ke src/main/assets/models/
    // SEBELUM build, dari salah satu sumber (prioritas: path lokal > URL):
    //  -BUBBLE_MODEL_PATH=/lokal/Manhwa-Translator/model/comic-speech-bubble-detector.onnx
    //  -BUBBLE_MODEL_URL=https://.../comic-speech-bubble-detector.onnx (diunduh CI)
    // Bila keduanya kosong → build lanjut TANPA bundle (detektor fallback file
    // manual di filesDir/models atau tombol Download di Settings → Models).
    // File hasil TIDAK di-commit (.gitignore: /app/src/main/assets/models/).
    val bubbleModelPath: String =
        (project.findProperty("BUBBLE_MODEL_PATH") as String?)
            ?: System.getenv("BUBBLE_MODEL_PATH") ?: ""
    val bubbleModelUrl: String =
        (project.findProperty("BUBBLE_MODEL_URL") as String?)
            ?: System.getenv("BUBBLE_MODEL_URL") ?: ""
    val bubbleAsset = layout.projectDirectory.file(
        "src/main/assets/models/comic-speech-bubble-detector.onnx"
    )
    tasks.register("bundleBubbleModel") {
        description = "Sediakan bubble .onnx di assets (path lokal atau URL, opsional)."
        onlyIf { !bubbleAsset.asFile.exists() || bubbleAsset.asFile.length() == 0L }
        doLast {
            bubbleAsset.asFile.parentFile?.mkdirs()
            if (bubbleModelPath.isNotBlank()) {
                val src = File(bubbleModelPath)
                require(src.exists() && src.length() > 0) {
                    "BUBBLE_MODEL_PATH tidak ditemukan: $bubbleModelPath"
                }
                src.copyTo(bubbleAsset.asFile, overwrite = true)
                println("bundleBubbleModel: disalin dari $bubbleModelPath")
            } else if (bubbleModelUrl.isNotBlank()) {
                println("bundleBubbleModel: mengunduh (build-time, atas konfigurasi user) ...")
                URI(bubbleModelUrl).toURL().openStream().use { ins: InputStream ->
                    bubbleAsset.asFile.outputStream().use { outs: OutputStream -> ins.copyTo(outs) }
                }
                println("bundleBubbleModel: selesai dari URL")
            } else {
                println("bundleBubbleModel: dilewati (tanpa PATH/URL) — APK tanpa bundle bubble")
            }
        }
    }
    tasks.findByName("preBuild")?.dependsOn("bundleBubbleModel")

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES"
            )
            // ONNX / OpenCV native .so harus 16KB-aligned untuk targetSdk 35+ nanti.
            // Lihat skill android-modernization-upgrade saat naik targetSdk.
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM — skill: android-compose-foundations
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // DI — skill: android-di-hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() di semua Screen
    ksp(libs.hilt.compiler)

    // Coroutines — skill: android-coroutines-flow
    implementation(libs.kotlinx.coroutines.android)

    // Coil — skill: android-coil-compose (thumbnail grid, preview efisien)
    implementation(libs.coil.compose)

    // Persistence — skill: android-room-database + android-local-persistence-datastore
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // API key storage — skill: android-security-best-practices
    implementation(libs.androidx.security.crypto)

    // Translation API — skill: android-networking-retrofit-okhttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ---- MVP-2 (ML) — AKTIF atas izin user (turn Step 5/MVP-2) ----
    // Dep di-download oleh CI saat build, BUKAN oleh saya lokal.
    // File .onnx TIDAK ikut APK — user memasukkan manual ke filesDir/models.
    implementation(libs.onnxruntime.mobile)      // YOLOv8m bubble + LaMa/MiGAN + PP-OCR ONNX
    implementation(libs.mlkit.text.recognition)  // ML Kit v2 text recognition (Latin, offline)

    testImplementation(libs.junit)
}
