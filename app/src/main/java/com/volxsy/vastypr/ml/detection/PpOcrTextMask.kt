package com.volxsy.vastypr.ml.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.volxsy.vastypr.ml.models.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Skill: android-architecture-clean — port Manhwa-Translator/local_text_mask.py
// (PP-OCRv6_small_det, file copy manual, TANPA pyclipper/OpenCV):
// resize limit-960 kelipatan 32, norm ImageNet, sigmoid-bila-perlu, thresh 0.3,
// komponen 8-konektivitas, UNCLIP PER-KOMPONEN (area*1.8/perimeter, via dilasi
// lokal — setara fallback dilate referensi), buang raksasa >40%, tighten
// open/close 3x3 + fringe 2px, upscale NEAREST. Layout output TERVERIFIKASI
// dari biner: 1 output prob-map [N,1,h,w] dinamis.
class PpOcrTextMask(
    private val models: ModelFiles,
) : TextMaskProvider {

    companion object {
        const val FILE_NAME = "PP-OCRv6_small_det.onnx"
        const val LIMIT = 960
        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        const val THRESH = 0.3f
        const val UNCLIP = 1.8f
        const val MIN_AREA = 8 // px pada resolusi model
        const val MAX_COVERAGE = 0.40f
        const val MIN_PIXELS = 15
        const val FRINGE = 2
        private val ONE: Byte = 1
    }

    private data class Comp(val area: Int, val l: Int, val t: Int, val r: Int, val b: Int)

    override suspend fun maskFor(image: Bitmap, region: List<PointF>?): MaskResult =
        withContext(Dispatchers.Default) {
            require(image.width > 0 && image.height > 0) { "Bitmap kosong" }
            val model = models.require(FILE_NAME, "PP-OCRv6 det")

            // Crop bbox region bila ada (referensi memproses per bubble-crop).
            var ox = 0
            var oy = 0
            var work = image
            var cropped: Bitmap? = null
            if (region != null && region.size >= 3) {
                val l = region.minOf { it.x }.toInt().coerceIn(0, image.width - 1)
                val t = region.minOf { it.y }.toInt().coerceIn(0, image.height - 1)
                val r = region.maxOf { it.x }.toInt().coerceIn(l + 1, image.width)
                val b = region.maxOf { it.y }.toInt().coerceIn(t + 1, image.height)
                if (r - l >= 8 && b - t >= 8) {
                    cropped = Bitmap.createBitmap(image, l, t, r - l, b - t)
                    ox = l
                    oy = t
                    work = cropped
                }
            }
            try {
                val (boxes, workMask) = processWork(model, work)
                // Tempel ke mask ukuran gambar penuh pada offset crop.
                val full = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ALPHA_8)
                val pix = IntArray(image.width * image.height)
                val mp = IntArray(workMask.width * workMask.height)
                workMask.getPixels(mp, 0, workMask.width, 0, 0, workMask.width, workMask.height)
                for (y in 0 until workMask.height) {
                    for (x in 0 until workMask.width) {
                        if ((mp[y * workMask.width + x] ushr 24) >= 128) {
                            pix[(oy + y) * image.width + (ox + x)] = -1 // alpha 255
                        }
                    }
                }
                full.setPixels(pix, 0, image.width, 0, 0, image.width, image.height)
                if (!workMask.isRecycled) workMask.recycle()
                val shifted = boxes.map { RectF(it.left + ox, it.top + oy, it.right + ox, it.bottom + oy) }
                MaskResult(full, shifted)
            } finally {
                if (cropped != null && !cropped.isRecycled) cropped.recycle()
            }
        }

    /** Proses 1 bitmap -> (boxes model-res diskala ke work, mask ALPHA_8 seukuran work). */
    private fun processWork(model: java.io.File, work: Bitmap): Pair<List<RectF>, Bitmap> {
        val ww = work.width
        val wh = work.height
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        env.createSession(model.absolutePath, opts).use { session ->
            val inName = session.inputNames.firstOrNull()
                ?: throw IllegalStateException("Model PP-OCR tanpa input")
            // Resize limit-960 kelipatan 32 (referensi _resize_for_det).
            val ratio = if (max(ww, wh) > LIMIT) LIMIT / max(ww, wh).toFloat() else 1f
            val rw = max((ww * ratio / 32).roundToInt() * 32, 32)
            val rh = max((wh * ratio / 32).roundToInt() * 32, 32)
            val small = Bitmap.createScaledBitmap(work, rw, rh, true)
            val px = IntArray(rw * rh)
            small.getPixels(px, 0, rw, 0, 0, rw, rh)
            if (!small.isRecycled) small.recycle()
            val n = rw * rh
            val buf = FloatBuffer.allocate(3 * n)
            for (i in px.indices) {
                val p = px[i]
                buf.put(i, (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0])
                buf.put(n + i, (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1])
                buf.put(2 * n + i, ((p and 0xFF) / 255f - MEAN[2]) / STD[2])
            }
            buf.rewind()
            OnnxTensor.createTensor(env, buf, longArrayOf(1L, 3L, rh.toLong(), rw.toLong())).use { t ->
                session.run(mapOf(inName to t)).use { res ->
                    val (pw, ph, prob) = extractProb(res.get(0).value)
                    return buildMask(prob, pw, ph, ww, wh)
                }
            }
        }
    }

    private data class Prob(val w: Int, val h: Int, val v: FloatArray)

    /** Ambil peta probabilitas (buang dim-1 depan), sigmoid bila di luar [0,1]. */
    private fun extractProb(v: Any?): Prob {
        var cur: Any? = v
        while (cur is Array<*> && cur.size == 1) cur = cur[0]
        val rows = cur as? Array<*>
            ?: throw IllegalStateException("Output PP-OCR tak dikenal")
        require(rows.isNotEmpty()) { "Output PP-OCR kosong" }
        val ph = rows.size
        val first = rows[0]
        val pw: Int
        val get: (Int, Int) -> Float
        when (first) {
            is FloatArray -> {
                pw = first.size
                get = { r, c -> (rows[r] as FloatArray)[c] }
            }
            is DoubleArray -> {
                pw = first.size
                get = { r, c -> (rows[r] as DoubleArray)[c].toFloat() }
            }
            else -> throw IllegalStateException("Output PP-OCR tak dikenal")
        }
        var mn = Float.POSITIVE_INFINITY
        var mx = Float.NEGATIVE_INFINITY
        val prob = FloatArray(pw * ph)
        for (y in 0 until ph) {
            for (x in 0 until pw) {
                val f = get(y, x)
                prob[y * pw + x] = f
                if (f < mn) mn = f
                if (f > mx) mx = f
            }
        }
        if (mn < -0.01f || mx > 1.01f) {
            for (i in prob.indices) prob[i] = (1f / (1f + exp(-prob[i])))
        }
        return Prob(pw, ph, prob)
    }

    /** Mask + boxes: komponen -> unclip per-komponen -> tighten -> upscale NEAREST. */
    private fun buildMask(prob: FloatArray, pw: Int, ph: Int, ww: Int, wh: Int): Pair<List<RectF>, Bitmap> {
        val empty = Bitmap.createBitmap(max(1, ww), max(1, wh), Bitmap.Config.ALPHA_8)
        val bin = ByteArray(pw * ph) { if (prob[it] > THRESH) ONE else 0 }
        val (label, comps) = labelComponents(bin, pw, ph)
        if (comps.isEmpty()) return emptyList<RectF>() to empty

        // Filter raksasa (>40% = border/ornamen, referensi _filter_huge_components).
        val total = pw * ph.toFloat()
        val kept = comps.filter { it.area >= MIN_AREA && it.area <= total * MAX_COVERAGE }
        if (kept.isEmpty()) return emptyList<RectF>() to empty

        // Perimeter per komponen (1 pass) untuk offset unclip.
        val perim = IntArray(comps.size)
        for (y in 0 until ph) {
            for (x in 0 until pw) {
                val id = label[y * pw + x]
                if (id == 0) continue
                if (x == 0 || label[y * pw + x - 1] != id ||
                    x == pw - 1 || label[y * pw + x + 1] != id ||
                    y == 0 || label[(y - 1) * pw + x] != id ||
                    y == ph - 1 || label[(y + 1) * pw + x] != id
                ) {
                    perim[id - 1]++
                }
            }
        }

        // Unclip per-komponen: dilasi lokal sesuai offsetnya sendiri.
        val full = ByteArray(pw * ph)
        val unionBoxes = mutableListOf<RectF>()
        kept.forEach { c ->
            val compIdx = comps.indexOf(c)
            val id = compIdx + 1
            val p = perim[compIdx]
            if (p <= 0) return@forEach
            val k = ((c.area * UNCLIP / p).roundToInt()).coerceAtLeast(0)
            val pad = k + 2
            val ll = max(0, c.l - pad)
            val tt = max(0, c.t - pad)
            val rr = min(pw - 1, c.r + pad)
            val bb = min(ph - 1, c.b + pad)
            val lw = rr - ll + 1
            val lh = bb - tt + 1
            var local = ByteArray(lw * lh) { j ->
                if (label[(tt + j / lw) * pw + (ll + j % lw)] == id) ONE else 0
            }
            repeat(k) { local = dilateCross(local, lw, lh) }
            var ul = lw
            var ut = lh
            var ur = -1
            var ub = -1
            for (j in local.indices) {
                if (local[j] != ONE) continue
                val gx = ll + j % lw
                val gy = tt + j / lw
                full[gy * pw + gx] = ONE
                if (gx < ul) ul = gx
                if (gx > ur) ur = gx
                if (gy < ut) ut = gy
                if (gy > ub) ub = gy
            }
            if (ur >= 0) unionBoxes += RectF(ul.toFloat(), ut.toFloat(), ur.toFloat(), ub.toFloat())
        }
        if (full.none { it == ONE }) return emptyList<RectF>() to empty

        // Tighten: OPEN 3x3, CLOSE 3x3, fringe DILATE 2 (referensi tighten_mask_for_telea).
        var m = open3(full, pw, ph)
        m = close3(m, pw, ph)
        repeat(FRINGE) { m = dilateCross(m, pw, ph) }

        var count = m.count { it == ONE }
        if (count < MIN_PIXELS) return emptyList<RectF>() to empty
        if (count > total * MAX_COVERAGE) {
            // Darurat: CLOSE + dilate 1px; masih kebesaran -> tolak.
            var e = close3(bin, pw, ph)
            e = dilateCross(e, pw, ph)
            if (e.count { it == ONE } > total * MAX_COVERAGE) {
                return emptyList<RectF>() to empty
            }
            m = e
        }

        // Upscale NEAREST ke ukuran work + boxes diskala.
        val out = Bitmap.createBitmap(ww, wh, Bitmap.Config.ALPHA_8)
        val pix = IntArray(ww * wh)
        for (y in 0 until wh) {
            val sy = min(ph - 1, (y * ph / wh))
            for (x in 0 until ww) {
                val sx = min(pw - 1, (x * pw / ww))
                if (m[sy * pw + sx] == ONE) pix[y * ww + x] = -1
            }
        }
        out.setPixels(pix, 0, ww, 0, 0, ww, wh)
        val sx = ww / pw.toFloat()
        val sy = wh / ph.toFloat()
        val boxes = unionBoxes.map {
            RectF(it.left * sx, it.top * sy, it.right * sx, it.bottom * sy)
        }
        return boxes to out
    }

    private fun labelComponents(bin: ByteArray, w: Int, h: Int): Pair<IntArray, List<Comp>> {
        val label = IntArray(w * h)
        val comps = mutableListOf<Comp>()
        val stack = ArrayDeque<Int>()
        for (i in bin.indices) {
            if (bin[i] != ONE || label[i] != 0) continue
            val id = comps.size + 1
            var area = 0
            var l = w
            var t = h
            var r = -1
            var b = -1
            stack.addLast(i)
            label[i] = id
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                val x = cur % w
                val y = cur / w
                area++
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val ni = ny * w + nx
                        if (bin[ni] == ONE && label[ni] == 0) {
                            label[ni] = id
                            stack.addLast(ni)
                        }
                    }
                }
            }
            comps += Comp(area, l, t, r, b)
        }
        return label to comps
    }

    private fun dilateCross(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = src.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (src[i] == ONE) continue
                if ((x > 0 && src[i - 1] == ONE) || (x < w - 1 && src[i + 1] == ONE) ||
                    (y > 0 && src[i - w] == ONE) || (y < h - 1 && src[i + w] == ONE)
                ) {
                    out[i] = ONE
                }
            }
        }
        return out
    }

    private fun erodeCross(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = src.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (src[i] != ONE) continue
                if ((x == 0 || src[i - 1] != ONE) || (x == w - 1 || src[i + 1] != ONE) ||
                    (y == 0 || src[i - w] != ONE) || (y == h - 1 || src[i + w] != ONE)
                ) {
                    out[i] = 0
                }
            }
        }
        return out
    }

    private fun open3(src: ByteArray, w: Int, h: Int): ByteArray =
        dilateCross(erodeCross(src, w, h), w, h)

    private fun close3(src: ByteArray, w: Int, h: Int): ByteArray =
        erodeCross(dilateCross(src, w, h), w, h)
}
