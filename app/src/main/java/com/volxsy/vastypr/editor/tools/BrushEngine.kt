package com.volxsy.vastypr.editor.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

// Skill: android-kotlin-core — stroke sebagai data murni agar undo/redo gampang.
// Render dilakukan di Canvas (EditorScreen), bukan di sini.
data class BrushStroke(
    val points: List<Offset> = emptyList(),
    val color: Color = Color.White,
    val sizePx: Float = 24f,
    val erase: Boolean = false,
) {
    fun toPath(): Path = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
}

object BrushEngine {
    fun append(stroke: BrushStroke, p: Offset): BrushStroke =
        stroke.copy(points = stroke.points + p)
}
