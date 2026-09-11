package com.volxsy.vastypr.editor.tools

import androidx.compose.ui.graphics.Color

// Skill: android-mobile-frontend-design — color wheel = HSV ring, palette = swatch cepat.
// Eyedrop: ambil pixel dari tile yang sedang tampil (diserahkan ke ViewModel via callback).
object ColorUtils {
    fun fromHsv(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
        val hsv = floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        val rgb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv)
        return Color(rgb)
    }

    fun toHex(c: Color): String {
        val a = (c.alpha * 255).toInt()
        val r = (c.red * 255).toInt()
        val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        return "#%02X%02X%02X%02X".format(a, r, g, b)
    }
}
