package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.R
import com.volxsy.vastypr.editor.model.EditorTool

// Skill: android-compose-foundations + android-compose-performance
// + android-compose-accessibility + android-mobile-frontend-design
// REDESIGN v2:
// - Rail dibungkus Surface (tonal + shadow) + label seksi "CANVAS TOOLS" / "AI TOOLS"
//   agar tidak tertutup canvas (dipadukan dengan clipToBounds + zIndex di Screen).
// - Chip premium: icon 20dp baru (stroke 2dp + fill), selected = primaryContainer
//   + border tegas + label semibold. Touch target >=48dp, contentDescription per tool.
// - AI row: FilledTonal chip + badge count untuk Clean, konsisten dengan tool rail.

private data class ToolItem(val tool: EditorTool, val icon: Int, val label: String, val desc: String)

private val CanvasTools = listOf(
    ToolItem(EditorTool.PAN, R.drawable.ic_tool_pan, "Pan", "Geser dan zoom canvas"),
    ToolItem(EditorTool.MOVE, R.drawable.ic_tool_move, "Move", "Pindah layer aktif"),
    ToolItem(EditorTool.SELECT_RECT, R.drawable.ic_tool_select, "Select", "Seleksi kotak"),
    ToolItem(EditorTool.SELECT_LASSO, R.drawable.ic_tool_lasso, "Lasso", "Seleksi bebas"),
    ToolItem(EditorTool.BRUSH, R.drawable.ic_tool_brush, "Brush", "Kuas dan penghapus"),
    ToolItem(EditorTool.EYEDROP, R.drawable.ic_tool_eyedrop, "Eyedrop", "Ambil warna"),
    ToolItem(EditorTool.TEXT, R.drawable.ic_tool_text, "Text", "Tambah teks"),
    ToolItem(EditorTool.CROP, R.drawable.ic_tool_crop, "Crop", "Potong non-destruktif"),
)

@Composable
fun ToolRail(
    current: EditorTool,
    onPick: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column {
            SectionLabel("Canvas tools")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CanvasTools, key = { it.tool }) { item ->
                    val selected = current == item.tool
                    FilterChip(
                        selected = selected,
                        onClick = { onPick(item.tool) },
                        enabled = enabled,
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(item.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = enabled,
                            selected = selected,
                            borderWidth = if (selected) 1.5.dp else 1.dp,
                        ),
                        modifier = Modifier.semantics { contentDescription = item.desc },
                    )
                }
            }
        }
    }
}

private data class AiItem(val key: String, val icon: Int, val label: String, val desc: String)

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
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column {
            SectionLabel("AI tools")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "bubble") {
                    AiChip(R.drawable.ic_tool_bubble, "Bubble", "Deteksi bubble", enabled, onBubble)
                }
                item(key = "ocr") {
                    AiChip(R.drawable.ic_tool_ocr, "OCR", "Baca teks gambar", enabled, onOcr)
                }
                item(key = "inpaint") {
                    AiChip(R.drawable.ic_tool_inpaint, "Inpaint", "Hapus teks", enabled, onInpaint)
                }
                if (onClean != null) {
                    item(key = "clean") {
                        val label = "Clean" + (cleanBadge?.let { " $it" } ?: "")
                        if (cleanBadge != null) {
                            BadgedBox(badge = { Badge { Text(cleanBadge.trim('(', ')', ' ')) } }) {
                                AiChip(R.drawable.ic_tool_clean, label, "Bersihkan semua bubble", enabled, onClean)
                            }
                        } else {
                            AiChip(R.drawable.ic_tool_clean, label, "Bersihkan semua bubble", enabled, onClean)
                        }
                    }
                }
                item(key = "translate") {
                    AiChip(R.drawable.ic_tool_translate, "Translate", "Terjemahkan teks", enabled, onTranslate)
                }
            }
        }
    }
}

@Composable
private fun AiChip(icon: Int, label: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.semantics { contentDescription = desc },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 14.dp, top = 8.dp)
            .semantics { contentDescription = text },
    )
}
