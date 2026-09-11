package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.R
import com.volxsy.vastypr.editor.model.EditorTool

// Skill: android-compose-foundations + android-compose-performance
// + android-compose-accessibility + android-mobile-frontend-design
// Fluid: LazyRow (fling 60fps + recycling) ganti Row+horizontalScroll yang
// me-recompose semua chip saat scroll. Target sentuh >=48dp via chip default.
// Stabil: param `enabled` mengunci tool saat AI/export sibuk (cegah double-tap).
@Composable
fun ToolRail(
    current: EditorTool,
    onPick: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tools: List<Triple<EditorTool, Int, String>> = listOf(
        Triple(EditorTool.PAN, R.drawable.ic_tool_pan, "Pan"),
        Triple(EditorTool.MOVE, R.drawable.ic_tool_move, "Move"),
        Triple(EditorTool.SELECT_RECT, R.drawable.ic_tool_select, "Select"),
        Triple(EditorTool.SELECT_LASSO, R.drawable.ic_tool_lasso, "Lasso"),
        Triple(EditorTool.BRUSH, R.drawable.ic_tool_brush, "Brush"),
        Triple(EditorTool.EYEDROP, R.drawable.ic_tool_eyedrop, "Eyedrop"),
        Triple(EditorTool.TEXT, R.drawable.ic_tool_text, "Text"),
        Triple(EditorTool.CROP, R.drawable.ic_tool_crop, "Crop"),
    )
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tools, key = { it.first }) { (t, res, label) ->
            FilterChip(
                selected = current == t,
                onClick = { onPick(t) },
                enabled = enabled,
                label = { Text(label) },
                leadingIcon = { Icon(painterResource(res), contentDescription = null) },
            )
        }
    }
}

@Composable
fun AiToolRow(
    onBubble: () -> Unit,
    onOcr: () -> Unit,
    onInpaint: () -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClean: (() -> Unit)? = null,
    cleanBadge: String? = null,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "bubble") {
            OutlinedButton(onClick = onBubble, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_tool_bubble), contentDescription = null)
                Text("  Bubble")
            }
        }
        item(key = "ocr") {
            OutlinedButton(onClick = onOcr, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_tool_ocr), contentDescription = null)
                Text("  OCR")
            }
        }
        item(key = "inpaint") {
            OutlinedButton(onClick = onInpaint, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_tool_inpaint), contentDescription = null)
                Text("  Inpaint")
            }
        }
        if (onClean != null) {
            item(key = "clean") {
                OutlinedButton(onClick = onClean, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_tool_clean), contentDescription = null)
                    Text("  Clean" + (cleanBadge?.let { " $it" } ?: ""))
                }
            }
        }
        item(key = "translate") {
            OutlinedButton(onClick = onTranslate, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_tool_translate), contentDescription = null)
                Text("  Translate")
            }
        }
    }
}
