package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.editor.model.DefaultPalette

// Skill: android-mobile-frontend-design — color wheel disederhanakan jadi palette + slider.
// FLUID rev2: palette jadi LazyRow (fling halus, tidak recompose semua saat scroll),
// swatch terpilih diberi ring agar 1-tap jelas. Slider size tetap 2..120px.
@Composable
fun ColorPanel(
    current: Color,
    sizePx: Float,
    onColor: (Color) -> Unit,
    onSize: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(DefaultPalette, key = { it.toArgb() }) { c ->
                val selected = c.toArgb() == current.toArgb()
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onColor(c) }
                )
            }
        }
        Text("Size: ${sizePx.toInt()}px", modifier = Modifier.padding(top = 8.dp))
        Slider(value = sizePx, onValueChange = onSize, valueRange = 2f..120f)
    }
}
