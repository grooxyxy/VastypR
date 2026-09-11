package com.volxsy.vastypr.ml.inpaint

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// Skill: android-architecture-clean — "Telea-lite" murni Kotlin (TANPA OpenCV).
// OpenCV Android SDK (~100MB+ modul lokal) sengaja TIDAK dipakai agar APK tetap
// kecil + tanpa download raksasa. Algoritma: difusi rata-rata 4-tetangga iteratif
// (gaya Gauss-Seidel) di dalam bbox mask — cocok untuk mask teks kecil-menengah.
// Mask besar/kompleks -> pakai backend LaMa/MiGAN (model manual user).
class DiffusionInpainter : Inpainter {

    companion object {
        const val MAX_ITER = 600
    }

    override suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height
            require(w > 0 && h > 0) { "Bitmap kosong" }
            val out = source.copy(Bitmap.Config.ARGB_8888, true)

            val mScaled = if (mask.width == w && mask.height == h) mask
            else Bitmap.createScaledBitmap(mask, w, h, false)
            val mp = IntArray(w * h)
            mScaled.getPixels(mp, 0, w, 0, 0, w, h)
            if (mScaled !== mask && !mScaled.isRecycled) mScaled.recycle()

            val pix = IntArray(w * h)
            out.getPixels(pix, 0, w, 0, 0, w, h)
            // Mask ALPHA_8: alpha >= 128 = area yang harus diisi.
            val known = BooleanArray(w * h) { (mp[it] ushr 24) < 128 }

            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if (!known[row + x]) {
                        if (x < l) l = x
                        if (x > r) r = x
                        if (y < t) t = y
                        if (y > b) b = y
                    }
                }
            }
            if (r < 0) return@withContext out // mask kosong -> kembalikan apa adanya
            l = max(0, l - 2)
            t = max(0, t - 2)
            r = min(w - 1, r + 2)
            b = min(h - 1, b + 2)

            var iter = 0
            var changed = true
            while (changed && iter < MAX_ITER) {
                changed = false
                iter++
                for (y in t..b) {
                    val row = y * w
                    for (x in l..r) {
                        val i = row + x
                        if (known[i]) continue
                        var sa = 0
                        var sr = 0
                        var sg = 0
                        var sb = 0
                        var n = 0
                        if (x > l && known[i - 1]) {
                            val p = pix[i - 1]
                            sa += (p ushr 24); sr += (p shr 16) and 0xFF
                            sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
                        }
                        if (x < r && known[i + 1]) {
                            val p = pix[i + 1]
                            sa += (p ushr 24); sr += (p shr 16) and 0xFF
                            sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
                        }
                        if (y > t && known[i - w]) {
                            val p = pix[i - w]
                            sa += (p ushr 24); sr += (p shr 16) and 0xFF
                            sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
                        }
                        if (y < b && known[i + w]) {
                            val p = pix[i + w]
                            sa += (p ushr 24); sr += (p shr 16) and 0xFF
                            sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
                        }
                        if (n > 0) {
                            pix[i] = (sa / n shl 24) or (sr / n shl 16) or (sg / n shl 8) or (sb / n)
                            known[i] = true
                            changed = true
                        }
                    }
                }
            }
            out.setPixels(pix, 0, w, 0, 0, w, h)
            out
        }
}
