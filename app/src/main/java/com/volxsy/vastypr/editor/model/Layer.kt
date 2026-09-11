package com.volxsy.vastypr.editor.model

import androidx.compose.ui.graphics.Color

// Skill: android-kotlin-core + android-architecture-clean
// Full layer system ala Photoshop: image/text/folder, clip, blend, lock, visibility.
// Immutable — semua mutasi lewat LayerManager + UndoRedoManager agar undo/redo konsisten.

enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY }

sealed interface Layer {
    val id: String
    val name: String
    val visible: Boolean
    val opacity: Float // 0..1
    val locked: Boolean
    val blend: BlendMode
    val clipToBelow: Boolean // clip layer (clipping mask ke layer bawah)

    data class Image(
        override val id: String,
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1f,
        override val locked: Boolean = false,
        override val blend: BlendMode = BlendMode.NORMAL,
        override val clipToBelow: Boolean = false,
        val bitmapPath: String? = null, // cache internal, bukan URI mentah
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scale: Float = 1f,
    ) : Layer

    data class Text(
        override val id: String,
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1f,
        override val locked: Boolean = false,
        override val blend: BlendMode = BlendMode.NORMAL,
        override val clipToBelow: Boolean = false,
        val content: String = "Double tap to edit",
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val style: VastTextStyle = VastTextStyle(),
    ) : Layer

    data class Folder(
        override val id: String,
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1f,
        override val locked: Boolean = false,
        override val blend: BlendMode = BlendMode.NORMAL,
        override val clipToBelow: Boolean = false,
        val expanded: Boolean = true,
        val children: List<Layer> = emptyList(),
    ) : Layer
}

fun Layer.withVisible(v: Boolean): Layer = when (this) {
    is Layer.Image -> copy(visible = v)
    is Layer.Text -> copy(visible = v)
    is Layer.Folder -> copy(visible = v)
}
fun Layer.withOpacity(o: Float): Layer = when (this) {
    is Layer.Image -> copy(opacity = o.coerceIn(0f, 1f))
    is Layer.Text -> copy(opacity = o.coerceIn(0f, 1f))
    is Layer.Folder -> copy(opacity = o.coerceIn(0f, 1f))
}
fun Layer.withClip(c: Boolean): Layer = when (this) {
    is Layer.Image -> copy(clipToBelow = c)
    is Layer.Text -> copy(clipToBelow = c)
    is Layer.Folder -> copy(clipToBelow = c)
}
fun Layer.withName(n: String): Layer = when (this) {
    is Layer.Image -> copy(name = n)
    is Layer.Text -> copy(name = n)
    is Layer.Folder -> copy(name = n)
}

// Helper warna default brush/palette
val DefaultPalette: List<Color> = listOf(
    Color.Black, Color.White, Color(0xFFFF5C5C), Color(0xFFFF9F1C),
    Color(0xFFFFD60A), Color(0xFF3ECF8E), Color(0xFF4CC9F0),
    Color(0xFF5B5BF0), Color(0xFF9D7BFF), Color(0xFFFF6FB5),
)
