package com.volxsy.vastypr.ml.inpaint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import com.volxsy.vastypr.ml.models.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.min

// Konfigurasi export ONNX. Default normalisasi [-1,1] (konvensi umum export LaMa;
// samakan dengan skrip export bila model user memakai konvensi lain).
data class OnnxInpaintSpec(
    val fileName: String,
    val label: String,
    val tile: Int = 512,
    val overlap: Int = 32,
    val mean: Float = 0.5f,
    val std: Float = 0.5f,
)

// Skill: android-architecture-clean — runner generik inpaint ONNX 2-input
// (image [1,3,H,W] RGB 0..1 ternormalisasi + mask [1,1,H,W]).
// Nama input dibaca dari session (heuristik "*mask*"); output pertama [1,3,H,W].
// Tile per bbox mask (fully-convolutional, ukuran tile native) agar hemat RAM.
// Session dibuat per panggilan lalu ditutup (model ratusan MB) — lihat YOLO.
class OnnxInpaintRunner(
    private val models: ModelFiles,
    private val spec: OnnxInpaintSpec,
) : Inpainter {

    override suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height
            require(w > 0 && h > 0) { "Bitmap kosong" }
            val model = models.require(spec.fileName, spec.label)

            val mScaled = if (mask.width == w && mask.height == h) mask
            else Bitmap.createScaledBitmap(mask, w, h, false)
            val mp = IntArray(w * h)
            mScaled.getPixels(mp, 0, w, 0, 0, w, h)
            if (mScaled !== mask && !mScaled.isRecycled) mScaled.recycle()

            val sp = IntArray(w * h)
            source.getPixels(sp, 0, w, 0, 0, w, h)

            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((mp[row + x] ushr 24) >= 128) {
                        if (x < l) l = x
                        if (x > r) r = x
                        if (y < t) t = y
                        if (y > b) b = y
                    }
                }
            }
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            if (r < 0) return@withContext out // mask kosong

            val pad = spec.overlap
            l = maxOf(0, l - pad)
            t = maxOf(0, t - pad)
            r = minOf(w - 1, r + pad)
            b = minOf(h - 1, b + pad)

            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            env.createSession(model.absolutePath, opts).use { session ->
                val names = session.inputNames.toList()
                val maskName = names.firstOrNull { it.contains("mask", ignoreCase = true) }
                val imgName = names.firstOrNull { it != maskName }
                require(names.size >= 2 && maskName != null && imgName != null) {
                    "Model ${spec.fileName}: butuh 2 input (image+mask), dapat $names"
                }
                val step = (spec.tile - spec.overlap).coerceAtLeast(64)
                var ty = t
                while (ty <= b) {
                    var tx = l
                    val th = min(spec.tile, h - ty)
                    while (tx <= r) {
                        val tw = min(spec.tile, w - tx)
                        runTile(env, session, imgName, maskName, sp, mp, w, out, tx, ty, tw, th)
                        if (tx + tw >= w) break
                        tx += step
                    }
                    if (ty + th >= h) break
                    ty += step
                }
            }
            out
        }

    private fun runTile(
        env: OrtEnvironment,
        session: OrtSession,
        imgName: String,
        maskName: String,
        sp: IntArray,
        mp: IntArray,
        stride: Int,
        out: Bitmap,
        tx: Int,
        ty: Int,
        tw: Int,
        th: Int,
    ) {
        val n = tw * th
        val imgBuf = FloatBuffer.allocate(3 * n)
        val mskBuf = FloatBuffer.allocate(n)
        for (y in 0 until th) {
            for (x in 0 until tw) {
                val i = (ty + y) * stride + (tx + x)
                val p = sp[i]
                val j = y * tw + x
                imgBuf.put(j, (((p shr 16) and 0xFF) / 255f - spec.mean) / spec.std)
                imgBuf.put(n + j, (((p shr 8) and 0xFF) / 255f - spec.mean) / spec.std)
                imgBuf.put(2 * n + j, ((p and 0xFF) / 255f - spec.mean) / spec.std)
                mskBuf.put(j, if ((mp[i] ushr 24) >= 128) 1f else 0f)
            }
        }
        imgBuf.rewind()
        mskBuf.rewind()
        OnnxTensor.createTensor(env, imgBuf, longArrayOf(1L, 3L, th.toLong(), tw.toLong())).use { imgT ->
            OnnxTensor.createTensor(env, mskBuf, longArrayOf(1L, 1L, th.toLong(), tw.toLong())).use { mskT ->
                session.run(mapOf(imgName to imgT, maskName to mskT)).use { res ->
                    val flat = FloatArray(3 * n)
                    val got = flattenToFloatArray(res.get(0).value, flat)
                    if (got < 3 * n) return // output tak terduga -> biarkan tile apa adanya
                    val px = IntArray(n)
                    for (j in 0 until n) {
                        val rr = ((flat[j] * spec.std + spec.mean) * 255f).toInt().coerceIn(0, 255)
                        val gg = ((flat[n + j] * spec.std + spec.mean) * 255f).toInt().coerceIn(0, 255)
                        val bb = ((flat[2 * n + j] * spec.std + spec.mean) * 255f).toInt().coerceIn(0, 255)
                        px[j] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                    }
                    // Tulis balik HANYA piksel bermask (konteks sekitar tetap asli).
                    for (y in 0 until th) {
                        for (x in 0 until tw) {
                            val i = (ty + y) * stride + (tx + x)
                            if ((mp[i] ushr 24) >= 128) sp[i] = px[y * tw + x]
                        }
                    }
                    val w = out.width
                    val h = out.height
                    out.setPixels(sp, 0, w, 0, 0, w, h)
                }
            }
        }
    }
}

/** Ratakan output tensor bersarang (FloatArray/DoubleArray) menjadi flat. */
internal fun flattenToFloatArray(v: Any?, out: FloatArray): Int {
    var idx = 0
    fun rec(o: Any?) {
        if (idx >= out.size) return
        when (o) {
            is FloatArray -> for (f in o) {
                if (idx >= out.size) return
                out[idx++] = f
            }
            is DoubleArray -> for (d in o) {
                if (idx >= out.size) return
                out[idx++] = d.toFloat()
            }
            is Array<*> -> for (e in o) rec(e)
        }
    }
    rec(v)
    return idx
}
