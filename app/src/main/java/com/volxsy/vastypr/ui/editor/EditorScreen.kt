package com.volxsy.vastypr.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.volxsy.vastypr.editor.model.BrushMode
import com.volxsy.vastypr.editor.model.EditorTool
import com.volxsy.vastypr.editor.model.Layer
import com.volxsy.vastypr.editor.model.TextEffect
import com.volxsy.vastypr.editor.model.VastAlign
import com.volxsy.vastypr.editor.model.VastTextStyle
import com.volxsy.vastypr.editor.model.composeAlign
import com.volxsy.vastypr.editor.model.composeDecoration
import com.volxsy.vastypr.editor.model.composeLineHeight
import com.volxsy.vastypr.editor.model.composeTracking
import com.volxsy.vastypr.editor.model.wordSpaced
import com.volxsy.vastypr.ui.editor.components.ColorPanel
import com.volxsy.vastypr.ui.editor.components.EditorTopBar
import com.volxsy.vastypr.ui.editor.components.ExportDialog
import com.volxsy.vastypr.ui.editor.components.LayerSheet
import com.volxsy.vastypr.ui.editor.components.TextEditorDialog
import com.volxsy.vastypr.ui.editor.components.ToolRail
import com.volxsy.vastypr.ui.editor.components.TranslateDialog

// Skill: android-compose-foundations + android-compose-state-effects
// + android-compose-performance + android-state-management + android-ui-states-validation
// FLUID+STABIL rev2:
// - Checkerboard 900-rect/frame DIHAPUS (penyebab jank utama) -> solid bg 1x.
// - Gesture dipisah: transformable HANYA saat PAN/MOVE, pointerInput HANYA saat
//   tool gambar (BRUSH/CROP/SELECT/LASSO). Sebelumnya keduanya aktif selalu dan
//   saling consume -> pinch ngaco + brush putus-putus.
// - Tombol AI dikunci saat isBusy (cegah job ganda -> OOM/crash).
// - Progress determinat per-bubble (busyLabel + busyProgress) agar Clean terasa hidup.
// - BottomSheet peek 96dp agar canvas dominan; sheet content dibatasi tingginya.
// - Empty state first-class bila sourceUri null (buka dari Home).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    onBack: () -> Unit,
    onOpenModels: (() -> Unit)? = null,
    vm: EditorViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strokes by vm.strokes.collectAsStateWithLifecycle()
    val fonts by vm.fontList.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scaffold = rememberBottomSheetScaffoldState()

    var showTextEditor by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showTranslate by remember { mutableStateOf(false) }
    var lastFormat by remember { mutableStateOf("png") }
    val fontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFont(uri) }

    // remember agar tidak scan layers tiap recomposition (fluid).
    val activeText: Layer.Text? = remember(state.layers, state.activeLayerId) {
        state.layers.filterIsInstance<Layer.Text>()
            .firstOrNull { it.id == state.activeLayerId }
    }

    LaunchedEffect(projectId) {
        vm.loadProject(projectId)
    }

    LaunchedEffect(Unit) {
        lastFormat = vm.lastExportFormat()
        vm.events.collect { e ->when (e) {
                is com.volxsy.vastypr.editor.model.EditorEvent.Message -> snack.showSnackbar(e.text)
                is com.volxsy.vastypr.editor.model.EditorEvent.ExportDone -> snack.showSnackbar("Exported: ${e.uri}")
                else -> Unit
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, offset, _ ->
        scale = (scale * zoom).coerceIn(0.25f, 8f)
        pan += offset
    }
    val isPanMode = state.tool == EditorTool.PAN || state.tool == EditorTool.MOVE
    val isDrawMode = state.tool == EditorTool.BRUSH ||
        state.tool == EditorTool.CROP ||
        state.tool == EditorTool.SELECT_RECT ||
        state.tool == EditorTool.SELECT_LASSO ||
        state.tool == EditorTool.EYEDROP
    val aiEnabled = !state.isBusy

    BottomSheetScaffold(
        scaffoldState = scaffold,
        sheetPeekHeight = 96.dp,
        topBar = {
            EditorTopBar(
                title = state.projectName,
                canUndo = state.canUndo, canRedo = state.canRedo,
                onBack = onBack, onUndo = vm::undo, onRedo = vm::redo,
                onExport = { if (!state.isBusy) showExport = true },
            )
        },
        sheetContent = {
            Column(Modifier.heightIn(max = 340.dp)) {
                LayerSheet(
                    layers = state.layers,
                    activeId = state.activeLayerId,
                    onSelect = vm::setActive,
                    onToggleVisible = vm::toggleVisible,
                    onOpacity = vm::setOpacity,
                    onAddImage = vm::addImageLayer,
                    onAddText = vm::addTextLayer,
                    onAddFolder = vm::addFolder,
                    onDuplicate = vm::duplicateActive,
                    onDelete = vm::deleteActive,
                )
                ColorPanel(
                    current = state.brushColor,
                    sizePx = state.brushSizePx,
                    onColor = { vm.setBrush(it, state.brushSizePx, state.brushMode) },
                    onSize = { vm.setBrush(state.brushColor, it, state.brushMode) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ToolRail(
                current = state.tool,
                onPick = vm::selectTool,
                enabled = aiEnabled,
            )
            com.volxsy.vastypr.ui.editor.components.AiToolRow(
                onBubble = vm::detectBubbles,
                onOcr = vm::runOcr,
                onInpaint = vm::runInpaint,
                onTranslate = {
                    if (activeText != null) showTranslate = true
                    else vm.toast("Pilih layer teks dulu, lalu Translate")
                },
                enabled = aiEnabled,
                onClean = vm::cleanAllBubbles,
                cleanBadge = if (state.bubbles.isNotEmpty()) "(${state.bubbles.size})" else null,
            )
            if (state.isBusy) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.busyLabel ?: "Memproses…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    state.busyProgress?.let {
                        Text(
                            "${(it * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val prog = state.busyProgress
                if (prog != null) {
                    LinearProgressIndicator(
                        progress = { prog.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            // ---- Canvas ----
            if (state.sourceUri == null) {
                // Empty state first-class: jangan tampilkan canvas kosong yang
                // membingungkan; arahkan balik ke Home.
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Belum ada gambar", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Buka project dari Home agar canvas tampil.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Kembali ke Home")
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .background(Color(0xFF23242F))
                        .onSizeChanged { vm.setCanvasSize(it.width, it.height) }
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = pan.x, translationY = pan.y,
                        )
                        // FLUID: hanya satu gesture aktif per mode. transformable
                        // tidak dipasang saat menggambar agar stroke tidak ke-consume.
                        .then(if (isPanMode) Modifier.transformable(transform) else Modifier)
                        .then(
                            if (isDrawMode) {
                                Modifier.pointerInput(state.tool) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val pos = ev.changes.firstOrNull()?.position ?: continue
                                            when (state.tool) {
                                                EditorTool.BRUSH -> {
                                                    ev.changes.forEach { it.consume() }
                                                    when (ev.type) {
                                                        androidx.compose.ui.input.pointer.PointerEventType.Press ->
                                                            vm.brushStart(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Move ->
                                                            vm.brushMove(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Release ->
                                                            vm.brushEnd()
                                                        else -> Unit
                                                    }
                                                }
                                                EditorTool.CROP -> {
                                                    ev.changes.forEach { it.consume() }
                                                    when (ev.type) {
                                                        androidx.compose.ui.input.pointer.PointerEventType.Press ->
                                                            vm.startCrop(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Move ->
                                                            vm.updateCrop(pos.x, pos.y)
                                                        else -> Unit
                                                    }
                                                }
                                                EditorTool.SELECT_RECT -> {
                                                    ev.changes.forEach { it.consume() }
                                                    when (ev.type) {
                                                        androidx.compose.ui.input.pointer.PointerEventType.Press ->
                                                            vm.startSelection(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Move ->
                                                            vm.updateSelection(pos.x, pos.y)
                                                        else -> Unit
                                                    }
                                                }
                                                EditorTool.SELECT_LASSO -> {
                                                    ev.changes.forEach { it.consume() }
                                                    when (ev.type) {
                                                        androidx.compose.ui.input.pointer.PointerEventType.Press ->
                                                            vm.startLasso(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Move ->
                                                            vm.appendLasso(pos.x, pos.y)
                                                        androidx.compose.ui.input.pointer.PointerEventType.Release ->
                                                            vm.finishLasso()
                                                        else -> Unit
                                                    }
                                                }
                                                EditorTool.EYEDROP -> {
                                                    if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                                        // MVP-1: eyedrop dari palette; sampling pixel tile di MVP-2.
                                                        vm.toast("Eyedrop: tap warna di panel bawah (sampling tile di MVP-2)")
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Gambar sumber project (Coil downsample ke viewport — aman OOM).
                    AsyncImage(
                        model = state.sourceUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    // Image layers yang menunjuk file (mis. hasil inpaint/clean).
                    state.layers.filterIsInstance<Layer.Image>()
                        .filter { it.visible && it.bitmapPath != null }
                        .forEach { img ->
                            AsyncImage(
                                model = java.io.File(img.bitmapPath!!),
                                contentDescription = img.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                alpha = img.opacity,
                            )
                        }
                    // Overlay ringan: strokes + AI boxes (tanpa checkerboard).
                    Canvas(Modifier.fillMaxSize()) {
                        // Brush strokes
                        strokes.forEach { s ->
                            if (s.points.size < 2) return@forEach
                            drawPath(
                                path = s.toPath(),
                                color = if (s.erase) Color.Transparent else s.color,
                                style = Stroke(width = s.sizePx),
                            )
                        }
                        // ---- Overlay seleksi/deteksi (koordinat normalisasi 0..1) ----
                        val fit = vm.fitRectFor(size.width, size.height)
                        fun nx(v: Float) = fit[0] + v * fit[2]
                        fun ny(v: Float) = fit[1] + v * fit[3]
                        // Bubble YOLO (hijau).
                        state.bubbles.forEach { b ->
                            drawRect(
                                color = Color(0xFF3ECF8E),
                                topLeft = Offset(nx(b.box.left), ny(b.box.top)),
                                size = Size(
                                    (b.box.right - b.box.left) * fit[2],
                                    (b.box.bottom - b.box.top) * fit[3]
                                ),
                                style = Stroke(width = 3f),
                            )
                        }
                        // OCR polygon (cyan).
                        state.ocrLines.forEach { l ->
                            if (l.polygon.size < 2) return@forEach
                            val path = Path().apply {
                                moveTo(nx(l.polygon[0].x), ny(l.polygon[0].y))
                                l.polygon.drop(1).forEach { lineTo(nx(it.x), ny(it.y)) }
                                close()
                            }
                            drawPath(path, Color(0xFF4CC9F0), style = Stroke(width = 3f))
                        }
                        // Selection rect (kuning).
                        state.selectionRect?.let { r ->
                            drawRect(
                                color = Color(0xFFFFD60A),
                                topLeft = Offset(nx(r.left), ny(r.top)),
                                size = Size((r.right - r.left) * fit[2], (r.bottom - r.top) * fit[3]),
                                style = Stroke(width = 4f),
                            )
                        }
                        // Lasso (magenta).
                        if (state.lassoPoints.size >= 2) {
                            val path = Path().apply {
                                moveTo(nx(state.lassoPoints[0].x), ny(state.lassoPoints[0].y))
                                state.lassoPoints.drop(1).forEach { lineTo(nx(it.x), ny(it.y)) }
                                if (state.lassoPoints.size >= 3) close()
                            }
                            drawPath(path, Color(0xFFFF6FB5), style = Stroke(width = 4f))
                        }
                        // Crop (putih).
                        state.cropRect?.let { r ->
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(nx(r.left), ny(r.top)),
                                size = Size((r.right - r.left) * fit[2], (r.bottom - r.top) * fit[3]),
                                style = Stroke(width = 5f),
                            )
                        }
                    }
                    // Text layers (efek ditumpuk: background + glow/shadow + stroke sederhana).
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(24.dp)) {
                        state.layers.filterIsInstance<Layer.Text>().filter { it.visible }.forEach { t ->
                            LayerText(t, vm.fontTypeface(t.style.fontId))
                        }
                    }
                }
            }
            // Quick brush mode toggle + Edit text (hanya bila layer teks aktif).
            Row(Modifier.padding(8.dp)) {
                FilterChip(
                    selected = state.brushMode == BrushMode.PAINT,
                    onClick = { vm.setBrush(state.brushColor, state.brushSizePx, BrushMode.PAINT) },
                    label = { Text("Brush") },
                    enabled = aiEnabled,
                )
                Spacer(Modifier.padding(4.dp))
                FilterChip(
                    selected = state.brushMode == BrushMode.ERASE,
                    onClick = { vm.setBrush(state.brushColor, state.brushSizePx, BrushMode.ERASE) },
                    label = { Text("Eraser") },
                    enabled = aiEnabled,
                )
                if (activeText != null) {
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = { showTextEditor = true },
                        enabled = aiEnabled,
                    ) {
                        Text("Edit text")
                    }
                }
            }
            // Aksi kontekstual: reset crop/seleksi/lasso/deteksi bila ada.
            if (state.cropRect != null || state.selectionRect != null ||
                state.lassoPoints.isNotEmpty() || state.bubbles.isNotEmpty() ||
                state.ocrLines.isNotEmpty()
            ) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    if (state.cropRect != null) {
                        OutlinedButton(onClick = vm::clearCrop, enabled = aiEnabled) { Text("Reset crop") }
                    }
                    if (state.selectionRect != null) {
                        OutlinedButton(onClick = vm::clearSelection, enabled = aiEnabled) { Text("Clear select") }
                    }
                    if (state.lassoPoints.isNotEmpty()) {
                        OutlinedButton(onClick = vm::clearLasso, enabled = aiEnabled) { Text("Clear lasso") }
                    }
                    if (state.bubbles.isNotEmpty() || state.ocrLines.isNotEmpty()) {
                        OutlinedButton(onClick = vm::clearDetections, enabled = aiEnabled) { Text("Clear AI") }
                    }
                }
            }
            if (showTextEditor && activeText != null) {
                TextEditorDialog(
                    initialContent = activeText.content,
                    initialStyle = activeText.style,
                    fonts = fonts,
                    getTypeface = vm::fontTypeface,
                    onImportFont = {
                        fontLauncher.launch(
                            arrayOf("font/ttf", "font/otf", "application/octet-stream")
                        )
                    },
                    onDeleteFont = vm::deleteFont,
                    onSaveStyle = vm::saveTextStyle,
                    onLoadStyle = vm::getSavedStyle,
                    onDone = { c, s ->
                        vm.updateActiveText(c, s)
                        showTextEditor = false
                    },
                    onDismiss = { showTextEditor = false },
                )
            }
            if (showExport) {
                ExportDialog(
                    initialName = state.projectName.ifBlank { "VastypR" },
                    initialFormat = lastFormat,
                    onDone = { name, format, longSide, quality ->
                        lastFormat = format
                        vm.exportCurrent(name, format, longSide, quality)
                        showExport = false
                    },
                    onDismiss = { showExport = false },
                )
            }
            if (showTranslate && activeText != null) {
                TranslateDialog(
                    sourceText = activeText.content,
                    onTranslate = { providerId, targetLang ->
                        vm.translatePreview(activeText.content, providerId, targetLang)
                    },
                    onApply = vm::applyTranslation,
                    onDismiss = { showTranslate = false },
                    onRenderToBubble = { translated ->
                        vm.renderTranslationToBubble(translated)
                        showTranslate = false
                    },
                    hasBubbleTarget = state.bubbles.isNotEmpty() || state.selectionRect != null,
                )
            }
        }
    }
}

@Composable
private fun LayerText(layer: Layer.Text, typeface: android.graphics.Typeface?) {
    // Skill: android-compose-foundations + android-compose-performance
    // Font custom (TTF/OTF import) dirender via AndroidView(TextView) agar 100% akurat
    // — tapi hanya bila efek kompatibel (tanpa Stroke/Gradient/Glow yang butuh overlay Compose).
    // Selain itu → StackedText (Compose) yang dukung semua 5 efek ditumpuk.
    val style = layer.style
    val hasComplex = style.effects.any {
        it is TextEffect.Stroke || it is TextEffect.GradientFill || it is TextEffect.OuterGlow
    }
    if (typeface != null && !hasComplex) {
        val bg = style.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
        val shadow = style.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
        AndroidView(
            factory = { ctx ->
                android.widget.TextView(ctx).apply {
                    applyTextViewProps(this, layer.content, style, typeface, bg, shadow)
                }
            },
            update = { tv -> applyTextViewProps(tv, layer.content, style, typeface, bg, shadow) },
            modifier = Modifier.padding(4.dp),
        )
    } else {
        StackedText(layer.content, style)
    }
}

private fun applyTextViewProps(
    tv: android.widget.TextView,
    content: String,
    style: VastTextStyle,
    typeface: android.graphics.Typeface,
    bg: TextEffect.Background?,
    shadow: TextEffect.DropShadow?,
) {
    // Jalur font custom: tracking/leading/alignment/dekorasi ala Photoshop.
    // Paragraph-spacing tidak didukung TextView native — fallback ke leading
    // (fidelity penuh ada di jalur Compose + export).
    val shown = if (style.allCaps) content.uppercase() else content
    val spanned = android.text.SpannableString(shown)
    if (style.wordSpacingEm > 0f) {
        // Emulasi word-spacing: regangkan tiap spasi via ScaleXSpan.
        val factor = 1f + style.wordSpacingEm * 4f
        shown.forEachIndexed { i, c ->
            if (c == ' ') spanned.setSpan(
                android.text.style.ScaleXSpan(factor),
                i, i + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    if (style.underline || style.strike) {
        var flags = tv.paintFlags
        if (style.underline) flags = flags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        if (style.strike) flags = flags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        tv.paintFlags = flags
    }
    tv.text = spanned
    val tfStyle = when {
        style.bold && style.italic -> android.graphics.Typeface.BOLD_ITALIC
        style.bold -> android.graphics.Typeface.BOLD
        style.italic -> android.graphics.Typeface.ITALIC
        else -> android.graphics.Typeface.NORMAL
    }
    tv.setTypeface(typeface, tfStyle)
    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp)
    tv.setTextColor(style.color.toArgb())
    tv.letterSpacing = style.letterSpacingEm
    tv.setLineSpacing(0f, style.lineHeightEm)
    when (style.align) {
        VastAlign.LEFT -> {
            tv.gravity = android.view.Gravity.START
            tv.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }
        VastAlign.CENTER -> {
            tv.gravity = android.view.Gravity.CENTER
            tv.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }
        VastAlign.RIGHT -> {
            tv.gravity = android.view.Gravity.END
            tv.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }
        VastAlign.JUSTIFY -> {
            tv.gravity = android.view.Gravity.START
            tv.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }
    }
    if (shadow != null) {
        tv.setShadowLayer(shadow.blur, shadow.dx, shadow.dy, shadow.color.toArgb())
    } else {
        tv.setShadowLayer(0f, 0f, 0f, 0)
    }
    tv.background = if (bg != null) {
        android.graphics.drawable.GradientDrawable().apply {
            setColor(bg.color.toArgb())
            cornerRadius = bg.cornerPx
        }
    } else {
        null
    }
    tv.setPadding(24, 16, 24, 16)
}

@Composable
private fun StackedText(
    text: String,
    style: VastTextStyle,
) {
    // Skill: android-compose-foundations — efek dirender berlapis sesuai urutan list,
    // tipografi (leading/tracking/word/paragraph/align/dekorasi) seragam via TextTypography.
    val bg = style.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
    val shadow = style.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
    val stroke = style.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
    val glow = style.effects.filterIsInstance<TextEffect.OuterGlow>().firstOrNull()
    val gradient = style.effects.filterIsInstance<TextEffect.GradientFill>().firstOrNull()

    val fontFamily = when (style.fontId) {
        null, "system_default" -> FontFamily.Default
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default // font import custom + efek kompleks → fallback Compose
    }
    val weight = if (style.bold) FontWeight.Bold else FontWeight.Normal
    val fStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal
    val base = MaterialTheme.typography.headlineSmall.copy(
        fontSize = style.fontSizeSp.sp,
        fontWeight = weight,
        fontStyle = fStyle,
        fontFamily = fontFamily,
        textAlign = style.composeAlign(),
        lineHeight = style.composeLineHeight(),
        letterSpacing = style.composeTracking(),
        textDecoration = style.composeDecoration(),
        shadow = shadow?.let {
            Shadow(color = it.color, offset = Offset(it.dx, it.dy), blurRadius = it.blur)
        },
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.padding(4.dp)
            .background(
                bg?.color ?: Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // Tiap overlay memakai struktur paragraf yang sama agar tumpukan presisi.
        @Composable
        fun Layer(render: @Composable (androidx.compose.ui.text.AnnotatedString) -> Unit) {
            androidx.compose.foundation.layout.Column {
                com.volxsy.vastypr.editor.model.splitParagraphs(text).forEachIndexed { i, para ->
                    if (i > 0) {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.heightIn(min = (style.paragraphSpacingEm * style.fontSizeSp).dp)
                        )
                    }
                    render(style.wordSpaced(para))
                }
            }
        }
        // Glow: copy ekstra di bawah dengan blur besar.
        if (glow != null) {
            Layer { ann ->
                Text(
                    ann,
                    style = base.copy(
                        color = glow.color,
                        shadow = Shadow(
                            color = glow.color,
                            offset = Offset.Zero,
                            blurRadius = glow.radius
                        ),
                    ),
                )
            }
        }
        if (stroke != null) {
            Layer { ann ->
                Text(
                    ann,
                    style = base.copy(color = stroke.color),
                    modifier = Modifier.graphicsLayer { translationX = 1.5f; translationY = 1.5f },
                )
            }
        }
        if (gradient != null && gradient.colors.size >= 2) {
            Layer { ann ->
                Text(
                    ann,
                    // copy(brush) dan copy(color) overload terpisah — brush menang saat render.
                    style = base.copy(
                        brush = Brush.linearGradient(gradient.colors),
                    ),
                )
            }
        } else {
            Layer { ann ->
                Text(
                    ann,
                    style = base.copy(color = style.color),
                )
            }
        }
    }
}
