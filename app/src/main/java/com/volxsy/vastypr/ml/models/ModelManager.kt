package com.volxsy.vastypr.ml.models

import android.content.Context
import com.volxsy.vastypr.data.prefs.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Skill: android-networking-retrofit-okhttp + android-coroutines-flow
// + android-local-persistence-datastore + android-security-best-practices
// Download MANUAL model ke app-private filesDir/models (tidak ikut backup,
// tidak perlu permission). Kecuali bubble detector (bundled saat build),
// semua model TIDAK ikut APK — hanya didownload bila user menekan Download.
// URL = override user, bila kosong pakai defaultUrl katalog (tautan resmi).
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: UserPrefs,
) {
    data class Status(
        val installed: Boolean = false,
        val sizeMb: Double = 0.0,
        val downloading: Boolean = false,
        val progress: Float = 0f, // 0..1 (-1 = ukuran tak diketahui)
        val error: String? = null,
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // file besar, tanpa batas baca
            .build()
    }

    // Status per modelId ("bubble" | "ppocr" | "lama" | "migan").
    private val states = MODEL_CATALOG.associate { it.modelId to MutableStateFlow(Status()) }

    // Kompat layar lama:
    val lama: Flow<Status> = states.getValue("lama").asStateFlow()
    val migan: Flow<Status> = states.getValue("migan").asStateFlow()
    // Baru:
    val bubble: Flow<Status> = states.getValue("bubble").asStateFlow()
    val ppocr: Flow<Status> = states.getValue("ppocr").asStateFlow()

    fun statusFlow(modelId: String): Flow<Status> =
        states[modelId]?.asStateFlow() ?: states.getValue("lama").asStateFlow()

    private var activeCall: okhttp3.Call? = null

    fun modelsDir(): File = File(ctx.filesDir, "models").apply { mkdirs() }
    fun fileFor(m: DownloadableModel): File = File(modelsDir(), m.fileName)

    /** URL efektif: override user bila diisi, else defaultUrl katalog. */
    suspend fun effectiveUrl(m: DownloadableModel): String {
        val override = when (m.modelId) {
            "lama" -> prefs.lamaUrl().first()
            "migan" -> prefs.miganUrl().first()
            "bubble" -> prefs.bubbleUrl().first()
            "ppocr" -> prefs.ppocrUrl().first()
            else -> ""
        }.trim()
        return override.ifBlank { m.defaultUrl }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        MODEL_CATALOG.forEach { m ->
            val f = fileFor(m)
            // Bubble: anggap "installed" juga bila tersedia di assets (bundled APK).
            val bundledReady = if (m.bundled) assetAvailable(m) else false
            val st = Status(
                installed = (f.exists() && f.length() > 0) || bundledReady,
                sizeMb = if (f.exists() && f.length() > 0) f.length() / 1048576.0 else 0.0,
            )
            states.getValue(m.modelId).update { st }
        }
    }

    /** Cek cepat apakah file bundled ada di assets/models (tanpa copy). */
    private fun assetAvailable(m: DownloadableModel): Boolean = runCatching {
        val names = listOf(m.fileName) + m.aliases
        val assets = ctx.assets.list("models")?.toSet() ?: emptySet()
        names.any { it in assets }
    }.getOrDefault(false)

    /** Ukuran pasti via HEAD (hanya dipanggil saat URL sudah ada). */
    suspend fun remoteSizeMb(url: String): Double? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url(url).head().build()
            http.newCall(req).execute().use { res ->
                res.header("Content-Length")?.toLongOrNull()?.div(1048576.0)
            }
        }.getOrNull()
    }

    suspend fun download(m: DownloadableModel, onDone: (Boolean, String) -> Unit) {
        val url = effectiveUrl(m)
        if (url.isBlank()) {
            onDone(false, "URL ${m.label} kosong — tempel link download dulu")
            return
        }
        setState(m, Status(downloading = true, progress = -1f))
        withContext(Dispatchers.IO) {
            val tmp = File(modelsDir(), m.fileName + ".part")
            try {
                val call = http.newCall(Request.Builder().url(url).header("User-Agent", "VastypR").build())
                activeCall = call
                call.execute().use { res ->
                    if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
                    val total = res.header("Content-Length")?.toLongOrNull() ?: -1L
                    val body = res.body ?: throw IllegalStateException("Body kosong")
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = body.byteStream().read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) setState(m, Status(downloading = true, progress = done.toFloat() / total))
                        }
                    }
                }
                tmp.renameTo(fileFor(m))
                refresh()
                onDone(true, "${m.label} terdownload")
            } catch (e: Exception) {
                tmp.delete()
                val msg = if (e is java.io.InterruptedIOException || e.message?.contains("Canceled") == true) {
                    "Download dibatalkan"
                } else e.message ?: "Download gagal"
                setState(m, Status(error = msg))
                onDone(false, msg)
            } finally {
                activeCall = null
            }
        }
    }

    fun cancel() { activeCall?.cancel() }

    suspend fun delete(m: DownloadableModel) = withContext(Dispatchers.IO) {
        fileFor(m).delete()
        // Jika backend aktif dihapus, kembali ke Telea agar editor tetap jalan.
        if (prefs.inpaintBackend.first() == m.backend.id && (m.modelId == "lama" || m.modelId == "migan")) {
            prefs.setInpaintBackend(InpaintBackend.TELEA.id)
        }
        refresh()
    }

    private fun setState(m: DownloadableModel, s: Status) {
        states[m.modelId]?.update { s }
    }
}
