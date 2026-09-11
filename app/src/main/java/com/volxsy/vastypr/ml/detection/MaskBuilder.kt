package com.volxsy.vastypr.ml.detection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF

// Mask sesuai bentuk teks (polygon OCR), bukan kotak bubble saja.
// Output: bitmap mask 1-channel (putih = area yang harus di-inpaint).
object MaskBuilder {
    fun fromPolygons(w: Int, h: Int, polys: List<List<PointF>>, dilatePx: Float = 4f): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(mask)
        val fill = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // Dilasi: gambar ulang tiap path sebagai stroke tebal agar mask
        // sedikit melebar menutup tepi huruf (tanpa dep OpenCV).
        val stroke = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (dilatePx * 2f).coerceAtLeast(0f)
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
        }
        polys.forEach { poly ->
            if (poly.size < 3) return@forEach
            val path = android.graphics.Path().apply {
                moveTo(poly[0].x, poly[0].y)
                poly.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            c.drawPath(path, fill)
            if (dilatePx > 0f) c.drawPath(path, stroke)
        }
        return mask
    }
}
