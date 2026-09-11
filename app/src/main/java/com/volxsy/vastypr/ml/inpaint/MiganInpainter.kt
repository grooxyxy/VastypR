package com.volxsy.vastypr.ml.inpaint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import com.volxsy.vastypr.ml.models.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

// Skill: android-architecture-clean — port Manhwa-Translator/migan_inpaint.py
// (model copy manual `migan_lxfater.onnx`, TANPA onnxruntime-python/cv2):
// input 4ch [mask_known-0.5, R*m, G*m, B*m] 512x512 ternormalisasi [-1,1],
// mask konvensi 255=KNOWN (dibalik dari mask hole 255=milik kita),
// output *0.5+0.5, feather (dilate 3x3 + Gaussian 5x5 s1.0) lalu blend.
// Dijalankan pada crop bbox hole (bukan full image). Gagal inferensi ->
// fallback DiffusionInpainter (seperti referensi fallback Telea); file model
// tidak ada -> MissingModelException berisi cara copy manual.
class MiganInpainter(
    private val models: ModelFiles,
) : Inpainter {

    companion object {
        const val FILE_NAME = "migan_lxfater.onnx" // sama seperti migan_inpaint.py
        const val SIZE = 512
        private val GAUSS5 = floatArrayOf(0.0545f, 0.2442f, 0.4026f, 0.2442f, 0.0545f)
    }

    override suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height
            require(w > 0 && h > 0) { "Bitmap kosong" }
            val model = models.require(FILE_NAME, "MiGAN")

            // Skala mask hole (255=hole) ke ukuran source.
            val hole = if (mask.width == w && mask.height == h) mask
            else Bitmap.createScaledBitmap(mask, w, h, false)
            val hp = IntArray(w * h)
            hole.getPixels(hp, 0, w, 0, 0, w, h)
            if (hole !== mask && !hole.isRecycled) hole.recycle()

            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if ((hp[y * w + x] ushr 24) >= 128) {
                        if (x < l) l = x
                        if (x > r) r = x
                        if (y < t) t = y
                        if (y > b) b = y
                    }
                }
            }
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            if (r < 0) return@withContext out // tidak ada hole
            val pad = 8
            l = max(0, l - pad)
            t = max(0, t - pad)
            r = min(w - 1, r + pad)
            b = min(h - 1, b + pad)
            val cw = r - l + 1
            val ch = b - t + 1

            try {
                runMiganCrop(model, source, hp, w, out, l, t, cw, ch)
            } catch (e: com.volxsy.vastypr.ml.models.MissingModelException) {
                throw e
            } catch (e: Exception) {
                // Fallback Telea-lite seperti referensi (fallback cv2 Telea).
                return@withContext DiffusionInpainter().inpaint(source, mask)
            }
            out
        }

    private fun runMiganCrop(
        model: java.io.File,
        source: Bitmap,
        holePx: IntArray,
        stride: Int,
        out: Bitmap,
        l: Int,
        t: Int,
        cw: Int,
        ch: Int,
    ) {
        val crop = Bitmap.createBitmap(source, l, t, cw, ch)
        val cp = IntArray(cw * ch)
        crop.getPixels(cp, 0, cw, 0, 0, cw, ch)
        if (!crop.isRecycled) crop.recycle()

        // Resize crop -> 512 (bilinear) + known mask -> 512 (nearest).
        val img512 = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        img512.setPixels(cp, 0, cw, 0, 0, cw, ch)
        val imgRs = Bitmap.createScaledBitmap(img512, SIZE, SIZE, true)
        if (!img512.isRecycled) img512.recycle()
        val ip = IntArray(SIZE * SIZE)
        imgRs.getPixels(ip, 0, SIZE, 0, 0, SIZE, SIZE)
        if (!imgRs.isRecycled) imgRs.recycle()

        // known = 255 - hole pada area crop, lalu ke 512 nearest.
        val knownCrop = IntArray(cw * ch)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val hole = (holePx[(t + y) * stride + (l + x)] ushr 24) >= 128
                knownCrop[y * cw + x] = if (hole) 0 else 255
            }
        }
        val knownBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ALPHA_8)
        val kpArgb = IntArray(cw * ch) { if (knownCrop[it] == 255) -1 else 0 }
        knownBmp.setPixels(kpArgb, 0, cw, 0, 0, cw, ch)
        val knownRs = Bitmap.createScaledBitmap(knownBmp, SIZE, SIZE, false)
        if (!knownBmp.isRecycled) knownBmp.recycle()
        val kp = IntArray(SIZE * SIZE)
        knownRs.getPixels(kp, 0, SIZE, 0, 0, SIZE, SIZE)
        if (!knownRs.isRecycled) knownRs.recycle()

        // Normalisasi + input 4ch [mask-0.5, R*m, G*m, B*m].
        val n = SIZE * SIZE
        val buf = FloatBuffer.allocate(4 * n)
        for (i in 0 until n) {
            val p = ip[i]
            val m = ((kp[i] ushr 24) / 255f)
            val rf = ((p shr 16) and 0xFF) / 255f * 2f - 1f
            val gf = ((p shr 8) and 0xFF) / 255f * 2f - 1f
            val bf = ((p and 0xFF)) / 255f * 2f - 1f
            buf.put(i, m - 0.5f)
            buf.put(n + i, rf * m)
            buf.put(2 * n + i, gf * m)
            buf.put(3 * n + i, bf * m)
        }
        buf.rewind()

        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        val out512: IntArray = env.createSession(model.absolutePath, opts).use { session ->
            val inName = session.inputNames.firstOrNull()
                ?: throw IllegalStateException("Model MiGAN tanpa input")
            OnnxTensor.createTensor(env, buf, longArrayOf(1L, 4L, SIZE.toLong(), SIZE.toLong())).use { ten ->
                session.run(mapOf(inName to ten)).use { res ->
                    val flat = FloatArray(3 * n)
                    val got = flattenToFloatArray(res.get(0).value, flat)
                    require(got >= 3 * n) { "Output MiGAN tak terduga" }
                    IntArray(n) { j ->
                        val rr = ((flat[j] * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                        val gg = ((flat[n + j] * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                        val bb = ((flat[2 * n + j] * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                        (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                    }
                }
            }
        }

        // Resize hasil -> ukuran crop (bilinear).
        val outBmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(out512, 0, SIZE, 0, 0, SIZE, SIZE)
        val outRs = Bitmap.createScaledBitmap(outBmp, cw, ch, true)
        if (!outBmp.isRecycled) outBmp.recycle()
        val op = IntArray(cw * ch)
        outRs.getPixels(op, 0, cw, 0, 0, cw, ch)
        if (!outRs.isRecycled) outRs.recycle()

        // Feather: dilate square 3x3 known-crop + Gaussian 5x5 s1.0.
        val known01 = FloatArray(cw * ch) { knownCrop[it] / 255f }
        val feather = gauss5x5(dilateSquare(known01, cw, ch), cw, ch)

        // Blend: orig*feather + migan*(1-feather), tulis balik ke out.
        val res = IntArray(cw * ch)
        for (i in 0 until cw * ch) {
            val f = feather[i]
            val a = cp[i]
            val o = op[i]
            val rr = (((a shr 16) and 0xFF) * f + ((o shr 16) and 0xFF) * (1f - f)).toInt().coerceIn(0, 255)
            val gg = (((a shr 8) and 0xFF) * f + ((o shr 8) and 0xFF) * (1f - f)).toInt().coerceIn(0, 255)
            val bb = ((a and 0xFF) * f + (o and 0xFF) * (1f - f)).toInt().coerceIn(0, 255)
            res[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        val resBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        resBmp.setPixels(res, 0, cw, 0, 0, cw, ch)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(resBmp, l.toFloat(), t.toFloat(), null)
        if (!resBmp.isRecycled) resBmp.recycle()
    }

    private fun dilateSquare(src: FloatArray, w: Int, h: Int): FloatArray {
        val out = src.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (src[i] >= 1f) continue
                var m = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        if (src[ny * w + nx] > m) m = src[ny * w + nx]
                    }
                }
                out[i] = m
            }
        }
        return out
    }

    private fun gauss5x5(src: FloatArray, w: Int, h: Int): FloatArray {
        val tmp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (k in -2..2) {
                    val nx = (x + k).coerceIn(0, w - 1)
                    s += src[y * w + nx] * GAUSS5[k + 2]
                }
                tmp[y * w + x] = s
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (k in -2..2) {
                    val ny = (y + k).coerceIn(0, h - 1)
                    s += tmp[ny * w + x] * GAUSS5[k + 2]
                }
                out[y * w + x] = s.coerceIn(0f, 1f)
            }
        }
        return out
    }
}
