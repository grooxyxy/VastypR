package com.volxsy.vastypr.ui.editor

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import com.volxsy.vastypr.ui.editor.components.blurCompat
import kotlin.math.roundToInt

// Skill: android-compose-foundations + android-compose-state-effects
// + android-compose-performance + android-state-management + android-ui-states-validation
// REV v3 (fix 8 issue):
// 1. Canvas TIDAK lagi menutupi rails: rails dibungkus Surface + zIndex(2f),
//    canvas di-clip via graphicsLayer(clip=true) di dalam wrapper weight(1f).
// 4. Text SELALU muncul: overlay Box terpusat + offset fraksi (offsetX/Y),
//    tap-to-select, border seleksi, auto-shadow bila tanpa efek (putih di atas
//    komik putih tetap terbaca), stroke 8-arah yang benar.
// 5. Seret HALUS: PAN hanya untuk canvas (MOVE memindah layer, bukan canvas),
//    zoom 0.5..5 + clamp pan + tombol zoom/reset + lockRotation.
// 7. Panel berguna: tab Layers/Brush + aksi cepat kontekstual.
// 8. Preview seleksi premium: dim + double-border + handle + grid crop + glow lasso.
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
    var panelTab by remember { mutableIntStateOf(0) }
    val fontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importFont(uri) }

    val activeText: Layer.Text? = remember(state.layers, state.activeLayerId) {
        state.layers.filterIsInstance<Layer.Text>()
            .firstOrNull { it.id == state.activeLayerId }
    }

    LaunchedEffect(projectId) {
        vm.loadProject(projectId)
    }

    LaunchedEffect(Unit) {
        lastFormat = vm.lastExportFormat()
        vm.events.collect { e ->
            when (e) {
                is com.volxsy.vastypr.editor.model.EditorEvent.Message -> snack.showSnackbar(e.text)
                is com.volxsy.vastypr.editor.model.EditorEvent.ExportDone -> snack.showSnackbar("Exported: ${e.uri}")
                else -> Unit
            }
        }
    }

    // ---- Smooth pan/zoom (fix #5): range dipersempit + clamp agar tidak liar ----
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    fun clampPan(p: Offset, s: Float): Offset {
        val m = 1400f * s
        return Offset(p.x.coerceIn(-m, m), p.y.coerceIn(-m, m))
    }
    val transform = rememberTransformableState { zoom, offset, _ ->
        scale = (scale * zoom).coerceIn(0.5f, 5f)
        // Damping ringan saat zoom-in agar tidak "keset": gerakan 1:1 terasa berat
        // saat scale besar, jadi normalisasi sedikit.
        val damp = 1f / scale.coerceAtLeast(1f).let { 0.7f + 0.3f * it }
        pan = clampPan(pan + offset * (1f + (1f - damp) * 0.5f), scale)
    }
    val isPanMode = state.tool == EditorTool.PAN
    val isMoveMode = state.tool == EditorTool.MOVE
    val isTextTapMode = state.tool == EditorTool.TEXT
    val isDrawMode = state.tool == EditorTool.BRUSH ||
        state.tool == EditorTool.CROP ||
        state.tool == EditorTool.SELECT_RECT ||
        state.tool == EditorTool.SELECT_LASSO ||
        state.tool == EditorTool.EYEDROP
    val aiEnabled = !state.isBusy

    BottomSheetScaffold(
        scaffoldState = scaffold,
        sheetPeekHeight = 112.dp,
        topBar = {
            EditorTopBar(
                title = state.projectName,
                canUndo = state.canUndo, canRedo = state.canRedo,
                onBack = onBack, onUndo = vm::undo, onRedo = vm::redo,
                onExport = { if (!state.isBusy) showExport = true },
            )
        },
        sheetContent = {
            Column(Modifier.heightIn(max = 430.dp)) {
                // Tab panel agar terasa berguna (fix #7)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = panelTab == 0,
                        onClick = { panelTab = 0 },
                        label = { Text("Layers (${state.layers.size})") },
                    )
                    FilterChip(
                        selected = panelTab == 1,
                        onClick = { panelTab = 1 },
                        label = { Text("Brush & Warna") },
                    )
                }
                if (panelTab == 0) {
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
                } else {
                    ColorPanel(
                        current = state.brushColor,
                        sizePx = state.brushSizePx,
                        onColor = { vm.setBrush(it, state.brushSizePx, state.brushMode) },
                        onSize = { vm.setBrush(state.brushColor, it, state.brushMode) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // FIX #1: rails di atas canvas (zIndex + elevasi), canvas ter-clip.
            ToolRail(
                current = state.tool,
                onPick = vm::selectTool,
                enabled = aiEnabled,
                modifier = Modifier.fillMaxWidth().zIndex(2f),
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
                modifier = Modifier.fillMaxWidth().zIndex(2f),
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
                        Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
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
            // ---- Canvas (wrapper clip + inner zoom) ----
            if (state.sourceUri == null) {
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
                // Wrapper: meng-clip semua overflow zoom agar tidak menutup rails (fix #1)
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .background(Color(0xFF23242F))
                        .zIndex(0f),
                ) {
                    val canvasW = state.canvasWidth.takeIf { it > 0 } ?: 1080
                    val canvasH = state.canvasHeight.takeIf { it > 0 } ?: 1920
                    Box(
                        Modifier.fillMaxSize()
                            .onSizeChanged { vm.setCanvasSize(it.width, it.height) }
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = pan.x, translationY = pan.y,
                                clip = true,
                            )
                            .then(if (isPanMode) Modifier.transformable(transform, lockRotationOnZoomPan = true) else Modifier)
                            .then(
                                when {
                                    isMoveMode -> Modifier.pointerInput(state.activeLayerId) {
                                        detectDragGestures(
                                            onDragEnd = { vm.nudgeActiveCommit() },
                                            onDragCancel = { vm.nudgeActiveCommit() },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val dx = dragAmount.x / canvasW.toFloat()
                                            val dy = dragAmount.y / canvasH.toFloat()
                                            vm.nudgeActiveLive(dx, dy)
                                        }
                                    }
                                    isTextTapMode -> Modifier.pointerInput(Unit) {
                                        detectTapGestures { pos ->
                                            val n = vm.canvasToNormalized(pos.x, pos.y)
                                            if (n != null) vm.addTextAt(n.x, n.y)
                                            else {
                                                // Tap di luar gambar: pilih text terdekat bila ada, else abaikan
                                                vm.toast("Tap di dalam gambar untuk tambah teks")
                                            }
                                        }
                                    }
                                    isDrawMode -> Modifier.pointerInput(state.tool) {
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
                                                            vm.toast("Eyedrop: tap warna di panel Brush & Warna")
                                                        }
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                        }
                                    }
                                    else -> Modifier
                                },
                            ),
                    ) {
                        AsyncImage(
                            model = state.sourceUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
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
                        // Overlay seleksi/deteksi premium (fix #8)
                        Canvas(Modifier.fillMaxSize()) {
                            strokes.forEach { s ->
                                if (s.points.size < 2) return@forEach
                                drawPath(
                                    path = s.toPath(),
                                    color = if (s.erase) Color.Transparent else s.color,
                                    style = Stroke(width = s.sizePx),
                                )
                            }
                            val fit = vm.fitRectFor(size.width, size.height)
                            fun nx(v: Float) = fit[0] + v * fit[2]
                            fun ny(v: Float) = fit[1] + v * fit[3]

                            // Bubble YOLO: fill + double border + dot sudut
                            state.bubbles.forEach { b ->
                                val tl = Offset(nx(b.box.left), ny(b.box.top))
                                val sz = Size(
                                    (b.box.right - b.box.left) * fit[2],
                                    (b.box.bottom - b.box.top) * fit[3],
                                )
                                drawRoundRect(
                                    color = Color(0xFF3ECF8E).copy(alpha = 0.14f),
                                    topLeft = tl, size = sz, cornerRadius = CornerRadius(10f, 10f),
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.9f),
                                    topLeft = tl, size = sz, cornerRadius = CornerRadius(10f, 10f),
                                    style = Stroke(width = 7f),
                                )
                                drawRoundRect(
                                    color = Color(0xFF3ECF8E),
                                    topLeft = tl, size = sz, cornerRadius = CornerRadius(10f, 10f),
                                    style = Stroke(width = 3.5f),
                                )
                                drawCircle(Color(0xFF3ECF8E), radius = 7f, center = tl)
                                drawCircle(
                                    Color(0xFF3ECF8E), radius = 7f,
                                    center = Offset(tl.x + sz.width, tl.y + sz.height),
                                )
                            }
                            // OCR polygon: fill + stroke + vertex
                            state.ocrLines.forEach { l ->
                                if (l.polygon.size < 2) return@forEach
                                val path = Path().apply {
                                    moveTo(nx(l.polygon[0].x), ny(l.polygon[0].y))
                                    l.polygon.drop(1).forEach { lineTo(nx(it.x), ny(it.y)) }
                                    close()
                                }
                                drawPath(path, Color(0xFF4CC9F0).copy(alpha = 0.18f))
                                drawPath(
                                    path, Color.White.copy(alpha = 0.85f),
                                    style = Stroke(width = 6f),
                                )
                                drawPath(path, Color(0xFF4CC9F0), style = Stroke(width = 3f))
                                l.polygon.forEach { p ->
                                    drawCircle(Color(0xFF4CC9F0), radius = 5f, center = Offset(nx(p.x), ny(p.y)))
                                }
                            }
                            // Selection rect: dim luar + fill + dashed double + handle
                            state.selectionRect?.let { r ->
                                val left = nx(r.left); val top = ny(r.top)
                                val w = (r.right - r.left) * fit[2]
                                val h = (r.bottom - r.top) * fit[3]
                                // Dim luar (4 rect)
                                val dim = Color.Black.copy(alpha = 0.45f)
                                drawRect(dim, Offset(0f, 0f), Size(size.width, top.coerceAtLeast(0f)))
                                val bottomTop = (top + h).coerceAtMost(size.height)
                                drawRect(dim, Offset(0f, bottomTop), Size(size.width, (size.height - bottomTop).coerceAtLeast(0f)))
                                drawRect(dim, Offset(0f, top), Size(left.coerceAtLeast(0f), h))
                                val rightLeft = (left + w).coerceAtMost(size.width)
                                drawRect(dim, Offset(rightLeft, top), Size((size.width - rightLeft).coerceAtLeast(0f), h))
                                val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                drawRoundRect(
                                    Color(0xFFFFD60A).copy(alpha = 0.16f),
                                    Offset(left, top), Size(w, h), CornerRadius(6f, 6f),
                                )
                                drawRoundRect(
                                    Color.White, Offset(left, top), Size(w, h),
                                    CornerRadius(6f, 6f), style = Stroke(width = 7f),
                                )
                                drawRoundRect(
                                    Color(0xFFFFD60A), Offset(left, top), Size(w, h),
                                    CornerRadius(6f, 6f),
                                    style = Stroke(width = 3.5f, pathEffect = dash),
                                )
                                listOf(
                                    Offset(left, top), Offset(left + w, top),
                                    Offset(left, top + h), Offset(left + w, top + h),
                                ).forEach { c ->
                                    drawCircle(Color.White, radius = 10f, center = c)
                                    drawCircle(Color(0xFFFFD60A), radius = 6.5f, center = c)
                                }
                            }
                            // Lasso: glow + solid + titik start + segmen penutup
                            if (state.lassoPoints.size >= 2) {
                                val pts = state.lassoPoints.map { Offset(nx(it.x), ny(it.y)) }
                                val path = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                                    if (pts.size >= 3) close()
                                }
                                drawPath(
                                    path, Color(0xFFFF6FB5).copy(alpha = 0.35f),
                                    style = Stroke(width = 11f),
                                )
                                drawPath(
                                    path, Color.White.copy(alpha = 0.9f),
                                    style = Stroke(width = 6f),
                                )
                                drawPath(path, Color(0xFFFF6FB5), style = Stroke(width = 3.5f))
                                drawCircle(Color.White, radius = 10f, center = pts[0])
                                drawCircle(Color(0xFFFF6FB5), radius = 6.5f, center = pts[0])
                            }
                            // Crop: dim + double border + grid thirds + handle
                            state.cropRect?.let { r ->
                                val left = nx(r.left); val top = ny(r.top)
                                val w = (r.right - r.left) * fit[2]
                                val h = (r.bottom - r.top) * fit[3]
                                val dim = Color.Black.copy(alpha = 0.55f)
                                drawRect(dim, Offset(0f, 0f), Size(size.width, top.coerceAtLeast(0f)))
                                val bottomTop = (top + h).coerceAtMost(size.height)
                                drawRect(dim, Offset(0f, bottomTop), Size(size.width, (size.height - bottomTop).coerceAtLeast(0f)))
                                drawRect(dim, Offset(0f, top), Size(left.coerceAtLeast(0f), h))
                                val rightLeft = (left + w).coerceAtMost(size.width)
                                drawRect(dim, Offset(rightLeft, top), Size((size.width - rightLeft).coerceAtLeast(0f), h))
                                drawRect(Color.White, Offset(left, top), Size(w, h), style = Stroke(width = 7f))
                                drawRect(Color(0xFF5B5BF0), Offset(left, top), Size(w, h), style = Stroke(width = 3f))
                                // thirds
                                val third = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                listOf(1f / 3f, 2f / 3f).forEach { f ->
                                    drawLine(
                                        Color.White.copy(alpha = 0.8f),
                                        Offset(left + w * f, top), Offset(left + w * f, top + h),
                                        strokeWidth = 2f, pathEffect = third,
                                    )
                                    drawLine(
                                        Color.White.copy(alpha = 0.8f),
                                        Offset(left, top + h * f), Offset(left + w, top + h * f),
                                        strokeWidth = 2f, pathEffect = third,
                                    )
                                }
                                listOf(
                                    Offset(left, top), Offset(left + w, top),
                                    Offset(left, top + h), Offset(left + w, top + h),
                                ).forEach { c ->
                                    drawCircle(Color.White, radius = 11f, center = c)
                                    drawCircle(Color(0xFF5B5BF0), radius = 7f, center = c)
                                }
                            }
                        }
                        // Text layers (fix #4: selalu muncul + bisa dipilih/diseret via MOVE)
                        TextOverlayBox(
                            layers = state.layers.filterIsInstance<Layer.Text>().filter { it.visible },
                            activeId = state.activeLayerId,
                            getTypeface = vm::fontTypeface,
                            onSelect = vm::setActive,
                            onEditRequest = { showTextEditor = true },
                        )
                    }
                    // Overlay kontrol zoom (tidak ikut zoom, fix #5)
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
                            Column(Modifier.padding(2.dp)) {
                                IconButton(onClick = {
                                    scale = (scale * 1.25f).coerceIn(0.5f, 5f)
                                }) { Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in") }
                                IconButton(onClick = {
                                    scale = (scale / 1.25f).coerceIn(0.5f, 5f)
                                    if (scale <= 0.55f) pan = Offset.Zero
                                }) { Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out") }
                                IconButton(onClick = {
                                    scale = 1f; pan = Offset.Zero
                                }) { Icon(Icons.Default.Refresh, contentDescription = "Reset zoom") }
                            }
                        }
                    }
                    // Badge zoom + hint tool
                    Surface(
                        Modifier.align(Alignment.TopStart).padding(10.dp),
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            "${(scale * 100).roundToInt()}% • ${toolHint(state.tool)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            // Quick brush/mode + Edit text
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    ) { Text("Edit text") }
                } else if (state.tool == EditorTool.TEXT) {
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { vm.addTextLayer() }, enabled = aiEnabled) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Tambah teks")
                    }
                }
            }
            // Info seleksi ringkas (membuat preview terasa nyambung, fix #8)
            SelectionInfoBar(
                hasCrop = state.cropRect != null,
                hasSel = state.selectionRect != null,
                lassoN = state.lassoPoints.size,
                bubbles = state.bubbles.size,
                ocr = state.ocrLines.size,
            )
            // Aksi kontekstual
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
                            arrayOf("font/ttf", "font/otf", "application/octet-stream"),
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

private fun toolHint(t: EditorTool): String = when (t) {
    EditorTool.PAN -> "seret/zoom canvas"
    EditorTool.MOVE -> "seret layer aktif"
    EditorTool.SELECT_RECT -> "seret kotak seleksi"
    EditorTool.SELECT_LASSO -> "gambar lasso"
    EditorTool.BRUSH -> "sapukan brush"
    EditorTool.EYEDROP -> "tap warna di panel"
    EditorTool.TEXT -> "tap gambar tambah teks"
    EditorTool.CROP -> "seret area crop"
}

@Composable
private fun SelectionInfoBar(hasCrop: Boolean, hasSel: Boolean, lassoN: Int, bubbles: Int, ocr: Int) {
    val parts = buildList {
        if (hasCrop) add("Crop aktif")
        if (hasSel) add("Select aktif")
        if (lassoN >= 3) add("Lasso $lassoN titik")
        if (bubbles > 0) add("$bubbles bubble")
        if (ocr > 0) add("$ocr OCR")
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" • "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/** Overlay teks: Box penuh, tiap teks diposisikan dari offsetX/Y fraksi (fix #4). */
@Composable
private fun TextOverlayBox(
    layers: List<Layer.Text>,
    activeId: String?,
    getTypeface: (String?) -> android.graphics.Typeface?,
    onSelect: (String) -> Unit,
    onEditRequest: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        layers.forEach { t ->
            val isActive = t.id == activeId
            // offset fraksi → px relatif: baseline proporsional stabil di semua HP.
            // (Export tetap memakai fraksi absolut — fidelity terjaga.)
            val offX = (t.offsetX * 620f)
            val offY = (t.offsetY * 900f)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        // Scrim legibility: aktif selalu ada highlight; non-aktif transparan
                        // kecuali punya Background effect (dirender di LayerText).
                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        else Color.Transparent,
                    )
                    .border(
                        width = if (isActive) 2.dp else 0.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        onSelect(t.id)
                        if (isActive) onEditRequest()
                    }
                    .padding(6.dp),
            ) {
                LayerText(t, getTypeface(t.style.fontId), isActive)
            }
        }
    }
}

@Composable
private fun LayerText(layer: Layer.Text, typeface: android.graphics.Typeface?, isActive: Boolean = false) {
    val style = layer.style
    // Jalur TextView hanya untuk efek sederhana (tanpa outline/gradient/glow/blur).
    // Outline/gradient/shadow-gradasi/blur butuh overlay Compose (StackedText).
    val stroke = style.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
    val hasComplex = stroke != null ||
        style.effects.any {
            it is TextEffect.GradientFill || it is TextEffect.OuterGlow ||
                it is TextEffect.Blur || (it is TextEffect.DropShadow && it.gradient != null)
        }
    if (typeface != null && !hasComplex) {
        val bg = style.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
        val shadow = style.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
        // Auto-legibility: teks terang tanpa efek di atas komik terang tetap terbaca.
        val autoShadow = shadow ?: if (!isActive && bg == null &&
            (style.color.red + style.color.green + style.color.blue) / 3f > 0.7f
        ) {
            TextEffect.DropShadow(Color.Black.copy(alpha = 0.75f), 0f, 3f, 9f)
        } else {
            null
        }
        AndroidView(
            factory = { ctx ->
                android.widget.TextView(ctx).apply {
                    applyTextViewProps(this, layer.content, style, typeface, bg, autoShadow)
                }
            },
            update = { tv -> applyTextViewProps(tv, layer.content, style, typeface, bg, autoShadow) },
            modifier = Modifier.padding(2.dp),
        )
    } else {
        StackedText(layer.content, style, isActive)
    }
}

@SuppressLint("WrongConstant")
private fun applyTextViewProps(
    tv: android.widget.TextView,
    content: String,
    style: VastTextStyle,
    typeface: android.graphics.Typeface,
    bg: TextEffect.Background?,
    shadow: TextEffect.DropShadow?,
) {
    val shown = if (style.allCaps) content.uppercase() else content
    val spanned = android.text.SpannableString(shown)
    if (style.wordSpacingEm > 0f) {
        val factor = 1f + style.wordSpacingEm * 4f
        shown.forEachIndexed { i, c ->
            if (c == ' ') {
                spanned.setSpan(
                    android.text.style.ScaleXSpan(factor),
                    i, i + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
    // FIX: reset flags dulu (sebelumnya |= menumpuk dan tidak bisa dimatikan).
    var flags = 0
    if (style.underline) flags = flags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    if (style.strike) flags = flags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
    tv.paintFlags = flags
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
    isActive: Boolean = false,
) {
    val bg = style.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
    val shadow = style.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
    val stroke = style.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
    val glow = style.effects.filterIsInstance<TextEffect.OuterGlow>().firstOrNull()
    val gradient = style.effects.filterIsInstance<TextEffect.GradientFill>().firstOrNull()
    val blur = style.effects.filterIsInstance<TextEffect.Blur>().firstOrNull()
    val shGrad = shadow?.gradient?.takeIf { it.size >= 2 }
    // Auto-shadow agar teks terang selalu muncul di atas komik terang (fix #4).
    // Dilewati bila shadow-gradasi sudah ada (salinannya yang memberi bayangan).
    val effShadow = shadow ?: if (shGrad == null && bg == null &&
        (style.color.red + style.color.green + style.color.blue) / 3f > 0.7f
    ) {
        TextEffect.DropShadow(Color.Black.copy(alpha = 0.8f), 0f, 4f, 10f)
    } else {
        null
    }

    val fontFamily = when (style.fontId) {
        null, "system_default" -> FontFamily.Default
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
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
        shadow = effShadow?.let {
            Shadow(color = it.color, offset = Offset(it.dx, it.dy), blurRadius = it.blur)
        },
    )
    Box(
        modifier = Modifier.padding(2.dp)
            .background(bg?.color ?: Color.Transparent, RoundedCornerShape(8.dp))
            .blurCompat(blur?.radius ?: 0f)
            .padding(6.dp),
    ) {
        @Composable
        fun Para(render: @Composable (androidx.compose.ui.text.AnnotatedString) -> Unit) {
            Column {
                com.volxsy.vastypr.editor.model.splitParagraphs(text).forEachIndexed { i, para ->
                    if (i > 0) {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.heightIn(min = (style.paragraphSpacingEm * style.fontSizeSp).dp),
                        )
                    }
                    render(style.wordSpaced(para))
                }
            }
        }
        if (glow != null) {
            Para { ann ->
                Text(
                    ann,
                    style = base.copy(
                        color = glow.color,
                        shadow = Shadow(color = glow.color, offset = Offset.Zero, blurRadius = glow.radius),
                    ),
                )
            }
        }
        // Stroke 8-arah yang benar; mendukung mode gradasi 2 warna.
        if (stroke != null) {
            val r = (stroke.widthPx / 3.5f).coerceIn(1f, 7f)
            val stGrad = stroke.gradient?.takeIf { it.size >= 2 }
            listOf(
                -r to 0f, r to 0f, 0f to -r, 0f to r,
                -r to -r, r to r, -r to r, r to -r,
            ).forEach { (dx, dy) ->
                Para { ann ->
                    Text(
                        ann,
                        style = if (stGrad != null) {
                            base.copy(
                                brush = Brush.linearGradient(stGrad),
                                shadow = Shadow(stGrad[0], Offset(dx, dy), 0.6f),
                            )
                        } else {
                            base.copy(
                                color = stroke.color,
                                shadow = Shadow(stroke.color, Offset(dx, dy), 0.6f),
                            )
                        },
                    )
                }
            }
        }
        // Shadow gradasi: salinan seposisi (tertutup teks utama), bayangannya mengintip.
        if (shGrad != null && shadow != null) {
            Para { ann ->
                Text(
                    ann,
                    style = base.copy(
                        brush = Brush.linearGradient(shGrad),
                        shadow = Shadow(shGrad[0], Offset(shadow.dx, shadow.dy), shadow.blur),
                    ),
                )
            }
        }
        if (gradient != null && gradient.colors.size >= 2) {
            Para { ann ->
                Text(
                    ann,
                    style = base.copy(
                        brush = Brush.linearGradient(gradient.colors),
                        shadow = if (shGrad != null) null else base.shadow,
                    ),
                )
            }
        } else {
            Para { ann ->
                Text(
                    ann,
                    style = base.copy(
                        color = style.color,
                        shadow = if (shGrad != null) null else base.shadow,
                    ),
                )
            }
        }
    }
}
