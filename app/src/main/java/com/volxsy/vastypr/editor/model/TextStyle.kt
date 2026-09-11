package com.volxsy.vastypr.editor.model

import androidx.compose.ui.graphics.Color

// Skill: android-kotlin-core — efek teks DITUMPUK (list), ala Photoshop Layer Style.
// Urutan render: fill -> gradient overlay -> stroke -> glow -> shadow.
sealed interface TextEffect {
    data class Stroke(val color: Color, val widthPx: Float) : TextEffect
    data class DropShadow(val color: Color, val dx: Float, val dy: Float, val blur: Float) : TextEffect
    data class OuterGlow(val color: Color, val radius: Float) : TextEffect
    data class GradientFill(val colors: List<Color>) : TextEffect
    data class Background(val color: Color, val cornerPx: Float, val paddingPx: Float) : TextEffect
}

// Skill: android-mobile-frontend-design — tipografi ala Photoshop Character/Paragraph.
// - align: LEFT/CENTER/RIGHT/JUSTIFY (menggantikan Boolean alignCenter lama).
// - lineHeightEm (Leading): jarak antar baris sebagai kelipatan font-size.
//   Berlaku tiap ganti baris "\n".
// - letterSpacingEm (Tracking): jarak antar huruf (em, bisa negatif).
// - wordSpacingEm: jarak ekstra antar kata (em, 0 = normal).
// - paragraphSpacingEm: spasi ekstra tiap batas paragraf (baris kosong "\n\n").
//   Untuk teks 2 baris biasa cukup lineHeightEm; paragraphSpacing dipakai saat
//   ada jeda paragraf.
// - allCaps/underline/strike: transformasi + dekorasi ala Photoshop.
enum class VastAlign { LEFT, CENTER, RIGHT, JUSTIFY }

data class VastTextStyle(
    val fontSizeSp: Float = 28f,
    val color: Color = Color.White,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val align: VastAlign = VastAlign.CENTER,
    // fontId: null = font sistem default; "sans"/"serif"/"mono" = family sistem;
    // nama file lain = font import di filesDir/fonts (lihat FontManager).
    val fontId: String? = null,
    // Tipografi paragraf (em relatif terhadap fontSizeSp).
    val lineHeightEm: Float = 1.25f, // Leading Photoshop (0.9..2.5)
    val letterSpacingEm: Float = 0f, // Tracking (-0.1..0.5)
    val wordSpacingEm: Float = 0f, // Word spacing (0..1)
    val paragraphSpacingEm: Float = 0f, // Space-after tiap paragraf (0..1.5)
    val allCaps: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    // Semua efek aktif ditumpuk sesuai urutan list.
    val effects: List<TextEffect> = listOf(
        TextEffect.Stroke(Color.Black, 6f),
        TextEffect.DropShadow(Color.Black.copy(alpha = 0.6f), 0f, 4f, 8f),
    ),
) {
    // Kompat: kode lama memakai alignCenter Boolean.
    val alignCenter: Boolean get() = align == VastAlign.CENTER
}
