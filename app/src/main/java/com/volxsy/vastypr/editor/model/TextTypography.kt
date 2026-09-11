package com.volxsy.vastypr.editor.model

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

// Skill: android-compose-foundations + android-kotlin-core
// Tipografi ala Photoshop, dipakai SERAGAM oleh:
// - TextEditorDialog (live preview)
// - EditorScreen.StackedText (canvas Compose)
// Tanpa dep baru — murni Compose ui-text.
fun VastTextStyle.displayText(content: String): String =
    if (allCaps) content.uppercase() else content

fun VastTextStyle.composeAlign(): TextAlign = when (align) {
    VastAlign.LEFT -> TextAlign.Start
    VastAlign.CENTER -> TextAlign.Center
    VastAlign.RIGHT -> TextAlign.End
    VastAlign.JUSTIFY -> TextAlign.Justify
}

fun VastTextStyle.composeDecoration(): TextDecoration {
    var d: TextDecoration? = null
    if (underline) d = TextDecoration.Underline
    if (strike) d = (d ?: TextDecoration.None) + TextDecoration.LineThrough
    return d ?: TextDecoration.None
}

/** Leading Photoshop: kelipatan font-size sebagai line-height Compose. */
fun VastTextStyle.composeLineHeight() = (lineHeightEm * fontSizeSp).sp

/** Tracking Photoshop untuk huruf biasa (spasi ditangani wordSpaced). */
fun VastTextStyle.composeTracking() = (letterSpacingEm * fontSizeSp).sp

/**
 * Word spacing Photoshop: spasi mendapat tracking + wordSpacingEm,
 * huruf biasa hanya tracking. Newline ikut chunk kata (tetap jadi break).
 */
fun VastTextStyle.wordSpaced(content: String): AnnotatedString {
    val shown = displayText(content)
    val track = composeTracking()
    val space = ((letterSpacingEm + wordSpacingEm) * fontSizeSp).sp
    // Jalur cepat: tanpa word-spacing, 1 span cukup.
    if (wordSpacingEm == 0f) {
        return buildAnnotatedString {
            pushStyle(SpanStyle(letterSpacing = track))
            append(shown)
            pop()
        }
    }
    return buildAnnotatedString {
        shown.split(" ").forEachIndexed { idx, word ->
            if (idx > 0) {
                pushStyle(SpanStyle(letterSpacing = space))
                append(" ")
                pop()
            }
            if (word.isNotEmpty()) {
                pushStyle(SpanStyle(letterSpacing = track))
                append(word)
                pop()
            }
        }
    }
}

/** Pecah konten jadi paragraf ("\n\n") untuk paragraph-spacing. */
fun splitParagraphs(content: String): List<String> = content.split("\n\n")
