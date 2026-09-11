package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.editor.model.DefaultPalette

// Skill: android-mobile-frontend-design — REDESIGN v2 agar panel terasa berguna:
// - Header + preview ukuran brush (lingkaran live sebesar sizePx, clamp visual).
// - Palette 40dp→44dp + ring 3dp + check implisit via border + contentDescription hex.
// - Preset ukuran (S/M/L/XL) 1-tap + slider halus 2..120px + label px tegas.
@Composable
fun ColorPanel(
    current: Color,
    sizePx: Float,
    onColor: (Color) -> Unit,
    onSize: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Brush",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            // Preview live ukuran brush
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size((sizePx.coerceIn(2f, 120f) / 120f * 36f + 6.dp.value).dp.coerceIn(8.dp, 42.dp))
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Text(
                "${sizePx.toInt()}px",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items((listOf(Color.Black, Color.White) + DefaultPalette).distinctBy { it.toArgb() }, key = { it.toArgb() }) { c ->
                val selected = c.toArgb() == current.toArgb()
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onColor(c) }
                        .semantics { contentDescription = "Warna #${Integer.toHexString(c.toArgb())}" },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf("S" to 8f, "M" to 24f, "L" to 48f, "XL" to 80f).forEach { (label, v) ->
                FilterChip(
                    selected = sizePx.toInt() == v.toInt(),
                    onClick = { onSize(v) },
                    label = { Text(label) },
                )
            }
        }
        Slider(
            value = sizePx,
            onValueChange = onSize,
            valueRange = 2f..120f,
            modifier = Modifier.semantics { contentDescription = "Ukuran brush ${sizePx.toInt()} pixel" },
        )
        Text(
            "Pilih warna lalu sapukan di canvas (tool Brush). Penghapus via toggle Brush/Eraser di atas canvas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
