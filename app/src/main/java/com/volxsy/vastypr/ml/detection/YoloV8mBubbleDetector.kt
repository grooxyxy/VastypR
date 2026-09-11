package com.volxsy.vastypr.ml.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.RectF
import com.volxsy.vastypr.ml.models.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Skill: android-architecture-clean + android-performance-observability
// Port langsung Manhwa-Translator/detect_bubbles.py + core.py (tanpa download):
// model copy manual `comic-speech-bubble-detector.onnx` (fallback nama lama
// `bubble_yolov8m.onnx`). Layout output TERVERIFIKASI dari biner via inspeksi
// lokal: in `images` [1,3,640,640], out `output0` [1,6,8400] = cx,cy,w,h + 2 skor.
// (Quirk transpose di kode Python dilewati — Kotlin memakai layout terverifikasi.)
// Aturan yang disalin persis: pad letterbox 114, round+offset int, SKOR MENTAH
// tanpa sigmoid (single-class langsung, multi-class argmax), conf 0.30 (jalur
// tiled), NMS IoU 0.45, tiling tinggi 1200/overlap 300 + NMS global.
// Session 99MB dibuka SEKALI per detect() dan dipakai ulang per tile.
class YoloV8mBubbleDetector(
    private val models: ModelFiles,
) : BubbleDetector {

    companion object {
        val FILE_CANDIDATES = listOf(
            "comic-speech-bubble-detector.onnx", // nama asli di Manhwa-Translator
            "bubble_yolov8m.onnx", // nama lama VastypR
        )
        const val INPUT = 640
        const val PAD = 114
        const val CONF_TH = 0.30f // default jalur tiled di core.py
        const val IOU_TH = 0.45f
        const val TILE_SIZE = 1200 // core.py TILE_SIZE (alasan: r~0.53 di 640)
        const val TILE_OVERLAP = 300 // core.py TILE_OVERLAP
    }

    private data class ScoredBox(val rect: RectF, val score: Float)

    override suspend fun detect(image: Bitmap): BubbleResult = withContext(Dispatchers.Default) {
        // filesDir/models dulu, lalu assets bundled (APK hasil bundleBubbleModel).
        val model = models.requireFirstOrBundled(FILE_CANDIDATES, "Bubble YOLOv8")
        require(image.width > 0 && image.height > 0) { "Bitmap kosong" }

        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        env.createSession(model.absolutePath, opts).use { session ->
            val inName = session.inputNames.firstOrNull()
                ?: throw IllegalStateException("Model bubble tanpa input")
            val tiles = splitTiles(image.height, TILE_SIZE, TILE_OVERLAP)
            val all = mutableListOf<ScoredBox>()
            for ((y0, y1) in tiles) {
                val th = y1 - y0
                if (th <= 0) continue
                val tile = Bitmap.createBitmap(image, 0, y0, image.width, th)
                try {
                    detectTile(env, session, inName, tile).forEach { b ->
                        all += ScoredBox(
                            RectF(b.rect.left, b.rect.top + y0, b.rect.right, b.rect.bottom + y0),
                            b.score,
                        )
                    }
                } finally {
                    if (!tile.isRecycled) tile.recycle()
                }
            }
            // NMS global antar-tile (core.py: hanya bila >1 tile).
            val final = if (tiles.size > 1 && all.isNotEmpty()) nms(all) else all
            BubbleResult(final.map { Bubble(it.rect, it.score) }, image.width, image.height)
        }
    }

    /** Port _split_tiles core.py: [(y_start, y_end)] dengan overlap. */
    internal fun splitTiles(height: Int, tileSize: Int, overlap: Int): List<Pair<Int, Int>> {
        require(tileSize > 0) { "tile_size harus >0" }
        require(overlap >= 0) { "overlap harus >=0" }
        require(overlap < tileSize) { "overlap harus < tile_size" }
        if (height <= tileSize) return listOf(0 to height)
        val tiles = mutableListOf<Pair<Int, Int>>()
        var y = 0
        while (y < height) {
            val yEnd = min(y + tileSize, height)
            tiles += y to yEnd
            if (yEnd >= height) break
            y = yEnd - overlap
        }
        return tiles
    }

    /** 1 tile -> letterbox 640 -> inferensi -> NMS lokal (koordinat tile). */
    private fun detectTile(
        env: OrtEnvironment,
        session: OrtSession,
        inName: String,
        tile: Bitmap,
    ): List<ScoredBox> {
        // Letterbox persis referensi: round, pad 114, offset int.
        val scale = min(INPUT / tile.width.toFloat(), INPUT / tile.height.toFloat())
        val nw = max(1, (tile.width * scale).roundToInt())
        val nh = max(1, (tile.height * scale).roundToInt())
        val dx = (INPUT - nw) / 2
        val dy = (INPUT - nh) / 2
        val small = Bitmap.createScaledBitmap(tile, nw, nh, true)
        val square = Bitmap.createBitmap(INPUT, INPUT, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(square).apply {
            drawColor((0xFF shl 24) or (PAD shl 16) or (PAD shl 8) or PAD)
            drawBitmap(small, dx.toFloat(), dy.toFloat(), null)
        }
        if (!small.isRecycled) small.recycle()

        val px = IntArray(INPUT * INPUT)
        square.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
        if (!square.isRecycled) square.recycle()
        val plane = INPUT * INPUT
        val buf = FloatBuffer.allocate(3 * plane)
        for (i in px.indices) {
            val p = px[i]
            buf.put(i, ((p shr 16) and 0xFF) / 255f)
            buf.put(plane + i, ((p shr 8) and 0xFF) / 255f)
            buf.put(plane * 2 + i, (p and 0xFF) / 255f)
        }
        buf.rewind()

        OnnxTensor.createTensor(
            env, buf, longArrayOf(1L, 3L, INPUT.toLong(), INPUT.toLong())
        ).use { tensor ->
            session.run(mapOf(inName to tensor)).use { res ->
                val boxes = parseOutput(
                    res.get(0).value, scale, dx.toFloat(), dy.toFloat(), tile.width, tile.height
                )
                return nms(boxes)
            }
        }
    }

    /**
     * Dukung [1,C,N] maupun [1,N,C]. Skor MENTAH seperti referensi:
     * 5 kolom -> kolom ke-4 langsung; >=6 kolom -> argmax mentah (semua kelas
     * dipertahankan, core.py tidak memfilter class_id).
     */
    private fun parseOutput(
        v: Any?, scale: Float, dx: Float, dy: Float, w: Int, h: Int,
    ): MutableList<ScoredBox> {
        val out = mutableListOf<ScoredBox>()
        val batch = (v as? Array<*>)?.getOrNull(0) as? Array<*> ?: return out
        if (batch.isEmpty()) return out
        val firstLen = (batch[0] as? FloatArray)?.size ?: return out
        val rows: List<FloatArray>
        val cols: Int
        if (batch.size <= 32 && firstLen > 32) {
            val c = batch.size
            val colArr = batch.map { it as FloatArray }
            rows = List(firstLen) { n -> FloatArray(c) { cc -> colArr[cc][n] } }
            cols = c
        } else {
            rows = batch.mapNotNull { it as? FloatArray }
            cols = firstLen
        }
        if (cols < 5) return out
        val singleClass = cols == 5
        for (r in rows) {
            val score = if (singleClass) {
                r[4]
            } else {
                var best = Float.NEGATIVE_INFINITY
                for (k in 4 until cols) if (r[k] > best) best = r[k]
                best
            }
            if (!score.isFinite() || score < CONF_TH) continue
            val bw = r[2] / scale
            val bh = r[3] / scale
            val cx = (r[0] - dx) / scale
            val cy = (r[1] - dy) / scale
            val rect = RectF(
                (cx - bw / 2f).coerceIn(0f, (w - 1).toFloat()),
                (cy - bh / 2f).coerceIn(0f, (h - 1).toFloat()),
                (cx + bw / 2f).coerceIn(0f, (w - 1).toFloat()),
                (cy + bh / 2f).coerceIn(0f, (h - 1).toFloat()),
            )
            if (rect.right > rect.left && rect.bottom > rect.top) {
                out += ScoredBox(rect, score)
            }
        }
        return out
    }

    private fun iou(a: RectF, b: RectF): Float {
        val l = max(a.left, b.left)
        val t = max(a.top, b.top)
        val r = min(a.right, b.right)
        val bo = min(a.bottom, b.bottom)
        val inter = max(0f, r - l) * max(0f, bo - t)
        if (inter <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun nms(boxes: List<ScoredBox>): List<ScoredBox> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.score }
        val kept = mutableListOf<ScoredBox>()
        for (b in sorted) {
            if (kept.none { iou(it.rect, b.rect) > IOU_TH }) kept += b
        }
        return kept
    }
}
