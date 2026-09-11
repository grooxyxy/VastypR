// Skill: android-gradle-build-logic + android-modularization + android-compose-performance
// Modul tunggal :app untuk MVP-1 agar mudah di-build di GitHub.
// Split ke :core/:feature saat codebase > ~30 file (lihat AGENTS.md handoff).
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
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
    // SEBELUM build, dari salah satu sumber (prioritas: path lokal > URL > default):
    //  -BUBBLE_MODEL_PATH=/lokal/model.onnx (atau env BUBBLE_MODEL_PATH)
    //  -BUBBLE_MODEL_URL=https://.../model.onnx (atau env/secret BUBBLE_MODEL_URL)
    //  - default: Google Drive milik user (link yang diberikan user, publik
    //    "Anyone with the link"):
    //    https://drive.google.com/file/d/13B42NV0mPPBzUUVsIvD4SvE3QXEaLOlv/view
    // Unduhan Drive memakai confirm-token + cookie (file ~99MB selalu kena
    // halaman peringatan virus-scan bila direct-download polos).
    // Hasil + verifikasi ukuran (>50MB) → APK langsung bisa deteksi bubble.
    // File hasil TIDAK di-commit (.gitignore: /app/src/main/assets/models/).
    val driveDefaultUrl =
        "https://drive.google.com/uc?export=download&id=13B42NV0mPPBzUUVsIvD4SvE3QXEaLOlv"
    val bubbleModelPath: String =
        (project.findProperty("BUBBLE_MODEL_PATH") as String?)
            ?: System.getenv("BUBBLE_MODEL_PATH") ?: ""
    val bubbleModelUrl: String =
        (project.findProperty("BUBBLE_MODEL_URL") as String?)
            ?: System.getenv("BUBBLE_MODEL_URL") ?: driveDefaultUrl
    val bubbleAsset = layout.projectDirectory.file(
        "src/main/assets/models/comic-speech-bubble-detector.onnx"
    )
    tasks.register("bundleBubbleModel") {
        description = "Sediakan bubble .onnx di assets (path lokal, URL, atau Drive default)."
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
            } else {
                println("bundleBubbleModel: mengunduh model bubble (~99MB, sekali per mesin CI) ...")
                downloadModelFile(bubbleModelUrl, bubbleAsset.asFile)
                val sz = bubbleAsset.asFile.length()
                require(sz > 50L * 1024 * 1024) {
                    "Unduhan mencurigakan (${sz} byte). " +
                        "Pastikan link Drive publik 'Anyone with the link' dan ID benar."
                }
                println("bundleBubbleModel: OK ${sz / 1024 / 1024}MB → bundled ke APK")
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

// Unduh file model dengan dukungan Google Drive besar (>25MB selalu kena
// halaman confirm virus-scan): ikuti redirect manual + cookie, lalu bila
// respons HTML cari token confirm dan ulangi dengan token tersebut.
// Murni java.net — tanpa dep baru. Dipakai bundleBubbleModel (build-time CI).
fun downloadModelFile(urlStr: String, dest: File) {
    var url = urlStr
    val cookies = LinkedHashMap<String, String>()
    var attempt = 0
    while (true) {
        attempt++
        require(attempt <= 6) { "Terlalu banyak redirect/confirm saat mengunduh model" }
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 30000
        conn.readTimeout = 300000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
        )
        if (cookies.isNotEmpty()) {
            conn.setRequestProperty(
                "Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            )
        }
        val code = conn.responseCode
        conn.headerFields["Set-Cookie"]?.forEach { sc ->
            val kv = sc.substringBefore(";")
            if ("=" in kv) cookies[kv.substringBefore("=")] = kv.substringAfter("=")
        }
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location")
                ?: throw org.gradle.api.GradleException("Redirect tanpa Location dari $url")
            url = if (loc.startsWith("http")) loc else URI(url).resolve(loc).toString()
            conn.disconnect()
            continue
        }
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw org.gradle.api.GradleException("HTTP $code saat mengunduh model dari $url")
        }
        val ctype = (conn.contentType ?: "").lowercase()
        if (ctype.contains("text/html")) {
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val token = Regex("[?&]confirm=([0-9A-Za-z_-]+)").find(html)
                ?.groupValues?.getOrNull(1)
                ?: Regex("confirm\\\\u003d([0-9A-Za-z_-]+)").find(html)
                    ?.groupValues?.getOrNull(1)
            if (token != null && "confirm=" !in url) {
                url = url + (if ("?" in url) "&" else "?") + "confirm=$token"
                continue
            }
            throw org.gradle.api.GradleException(
                "Drive mengembalikan halaman HTML, bukan file. " +
                    "Pastikan link publik 'Anyone with the link' dan ID file benar."
            )
        }
        conn.inputStream.use { ins ->
            dest.outputStream().use { outs -> ins.copyTo(outs) }
        }
        conn.disconnect()
        return
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
