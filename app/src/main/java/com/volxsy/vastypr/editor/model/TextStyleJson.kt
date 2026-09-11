package com.volxsy.vastypr.editor.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

// Skill: android-serialization-offline-sync + android-kotlin-core
// Save/load text style sebagai JSON (org.json bawaan Android — tanpa dep baru).
// Dipakai tombol Save/Load di TextEditorDialog + UserPrefs.TEXT_STYLE_JSON.
// Kompat mundur: file lama memakai "alignCenter" Boolean — tetap dibaca
// (dipetakan ke VastAlign), lalu disimpan ulang memakai "align".
object TextStyleJson {

    fun encode(s: VastTextStyle): String = JSONObject()
        .put("fontSizeSp", s.fontSizeSp.toDouble())
        .put("color", s.color.toArgb())
        .put("bold", s.bold)
        .put("italic", s.italic)
        .put("align", s.align.name)
        .put("fontId", s.fontId)
        .put("lineHeightEm", s.lineHeightEm.toDouble())
        .put("letterSpacingEm", s.letterSpacingEm.toDouble())
        .put("wordSpacingEm", s.wordSpacingEm.toDouble())
        .put("paragraphSpacingEm", s.paragraphSpacingEm.toDouble())
        .put("allCaps", s.allCaps)
        .put("underline", s.underline)
        .put("strike", s.strike)
        .put("effects", JSONArray().apply {
            s.effects.forEach { put(encodeEffect(it)) }
        })
        .toString()

    fun decode(json: String): VastTextStyle? = runCatching {
        val o = JSONObject(json)
        // Legacy: "alignCenter" Boolean (rev1). Baru: "align" String.
        val align = runCatching { VastAlign.valueOf(o.optString("align", "")) }
            .getOrNull()
            ?: if (o.optBoolean("alignCenter", true)) VastAlign.CENTER else VastAlign.LEFT
        VastTextStyle(
            fontSizeSp = o.optDouble("fontSizeSp", 28.0).toFloat(),
            color = Color(o.optInt("color", Color.White.toArgb())),
            bold = o.optBoolean("bold", true),
            italic = o.optBoolean("italic", false),
            align = align,
            fontId = o.optString("fontId", null.toString()).takeIf { it != "null" },
            lineHeightEm = o.optDouble("lineHeightEm", 1.25).toFloat().coerceIn(0.9f, 2.5f),
            letterSpacingEm = o.optDouble("letterSpacingEm", 0.0).toFloat().coerceIn(-0.1f, 0.5f),
            wordSpacingEm = o.optDouble("wordSpacingEm", 0.0).toFloat().coerceIn(0f, 1f),
            paragraphSpacingEm = o.optDouble("paragraphSpacingEm", 0.0).toFloat().coerceIn(0f, 1.5f),
            allCaps = o.optBoolean("allCaps", false),
            underline = o.optBoolean("underline", false),
            strike = o.optBoolean("strike", false),
            effects = buildList {
                val arr = o.optJSONArray("effects") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    decodeEffect(arr.optJSONObject(i))?.let { add(it) }
                }
            }.ifEmpty {
                listOf(
                    TextEffect.Stroke(Color.Black, 6f),
                    TextEffect.DropShadow(Color.Black.copy(alpha = 0.6f), 0f, 4f, 8f),
                )
            },
        )
    }.getOrNull()

    private fun encodeEffect(e: TextEffect): JSONObject = when (e) {
        is TextEffect.Stroke -> JSONObject()
            .put("t", "stroke").put("c", e.color.toArgb()).put("w", e.widthPx.toDouble())
        is TextEffect.DropShadow -> JSONObject()
            .put("t", "shadow").put("c", e.color.toArgb())
            .put("dx", e.dx.toDouble()).put("dy", e.dy.toDouble()).put("b", e.blur.toDouble())
        is TextEffect.OuterGlow -> JSONObject()
            .put("t", "glow").put("c", e.color.toArgb()).put("r", e.radius.toDouble())
        is TextEffect.GradientFill -> JSONObject()
            .put("t", "grad").put("cs", JSONArray(e.colors.map { it.toArgb() }))
        is TextEffect.Background -> JSONObject()
            .put("t", "bg").put("c", e.color.toArgb())
            .put("cr", e.cornerPx.toDouble()).put("pd", e.paddingPx.toDouble())
    }

    private fun decodeEffect(o: JSONObject?): TextEffect? {
        if (o == null) return null
        return when (o.optString("t")) {
            "stroke" -> TextEffect.Stroke(Color(o.optInt("c")), o.optDouble("w", 6.0).toFloat())
            "shadow" -> TextEffect.DropShadow(
                Color(o.optInt("c")), o.optDouble("dx").toFloat(),
                o.optDouble("dy").toFloat(), o.optDouble("b").toFloat(),
            )
            "glow" -> TextEffect.OuterGlow(Color(o.optInt("c")), o.optDouble("r", 12.0).toFloat())
            "grad" -> {
                val arr = o.optJSONArray("cs") ?: return null
                TextEffect.GradientFill(List(arr.length()) { Color(arr.optInt(it)) })
            }
            "bg" -> TextEffect.Background(
                Color(o.optInt("c")), o.optDouble("cr", 8.0).toFloat(), o.optDouble("pd", 8.0).toFloat(),
            )
            else -> null
        }
    }
}
