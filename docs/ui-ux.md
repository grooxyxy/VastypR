# VastypR UI/UX — rev3 clip + wheel + efek pro (tanpa build)

Skill: `android-mobile-frontend-design` (create mode) + `android-material3-design-system`.
Postur: **confident utility**, dark-first (#14141F) agar mata nyaman edit gambar tall.
File mockup SVG (buka di browser / Acode preview):
- `docs/mockup-home.svg` — Home rev2 (thumbnail + delete + 2 import)
- `docs/mockup-editor.svg` — Editor rev3 (clip rails + zoom ctrl + seleksi premium + wheel)
- `docs/mockup-settings.svg` — Settings rev2 (3 kartu sectioned)

Prinsip rev3: **fluid = 60fps saat scroll/gambar; stabil = tak ada job ganda, tak ada OOM.**
Tanpa download model/dependencies oleh saya. Tanpa dep baru
(wheel + gradasi + blur murni Compose/Canvas bawaan).

## 0. Apa yang berubah dari rev1 (dan kenapa)

| Masalah rev1 (jangk/stuck) | Perbaikan rev2 | File |
|---|---|---|
| Checkerboard digambar ~900 `drawRect` tiap frame di `Canvas` | Dihapus → `Modifier.background(solid)` 1x; Canvas hanya strokes + overlay AI | `EditorScreen.kt` |
| `transformable` + `pointerInput` aktif bersamaan → pinch vs brush saling consume | Dipisah: `transformable` hanya PAN/MOVE, `pointerInput` hanya tool gambar | `EditorScreen.kt` |
| Tombol AI bisa di-tap 2x → 2 inferensi ORT bersamaan → ANR/OOM | `isBusy` guard di VM + `enabled=!isBusy` di UI | `EditorViewModel.kt`, `EditorScreen.kt`, `ToolRail.kt` |
| Inferensi/deteksi jalan di Main thread → frame drop | `withContext(Dispatchers.Default)` untuk detect/OCR/mask/inpaint | `EditorViewModel.kt` |
| `ToolRail` pakai `Row+horizontalScroll` → recompose semua chip saat scroll | `LazyRow` + `key` (fling + recycling) | `ToolRail.kt` |
| `BottomSheet` tanpa batas → measure loop + canvas kejepit | `sheetPeekHeight=96dp` + sheet content `heightIn(max=340dp)`, `LayerSheet` list `max=180dp` | `EditorScreen.kt`, `LayerSheet.kt` |
| `ColorPanel` Row penuh → jank + seleksi tak jelas | `LazyRow` + ring seleksi | `ColorPanel.kt` |
| FAB Home mati (emit event tanpa handler) | Handler Photo Picker + fallback SAF, `importUri` beneran ke Room | `HomeScreen.kt`, `HomeViewModel.kt` |
| Home grid teks saja, tanpa hapus | Thumbnail Coil + tombol delete + loading/error/empty first-class | `HomeScreen.kt` |
| Settings 1 form panjang | 3 kartu: Terjemahan / Inpaint & Models / Tentang | `SettingsScreen.kt` |
| Translate hanya "Pakai" ke layer aktif | Tambah "Render ke bubble" (auto-fit font di posisi bubble) | `TranslateDialog.kt`, `EditorViewModel.renderTranslationToBubble()` |
| Clean per-bubble belum ada (NEXT_STEP P1) | `cleanAllBubbles()` + tombol Clean + progress per-bubble | `EditorViewModel.kt`, `ToolRail.kt` |

## 1. Home — 1 aksi primer jelas (rev2)
```
┌─────────────────────────┐
│        VastypR     [⚙]  │  CenterAlignedTopAppBar
├─────────────────────────┤
│ ┌─────┐ ┌─────┐         │
│ │thumb│ │thumb│         │  Grid adaptive 150dp, key=id
│ │Solo │ │ORV  │ [🗑]    │  ElevatedCard: thumbnail Coil crop 0.72 + nama + meta + delete
│ │720x │ │720x │         │
│ └─────┘ └─────┘         │
│ [Galeri] [Files]        │  Empty: 2 tombol (Picker + SAF fallback)
│              [＋ Import] │  ExtendedFAB → Photo Picker ImageOnly
│ loading: spinner tengah │
│ error: snackbar + clear │
└─────────────────────────┘
```
- Sukses: thumbnail `AsyncImage(p.sourceUri)` + `720x15400` meta + delete.
- Loading: `CircularProgressIndicator` tengah. Error: snackbar (first-class).
- A11y: contentDescription "Open <nama>" + "Hapus <nama>", FAB 48dp+.

## 2. Editor — ala ibisPaint, 1 tangan bisa (rev2)
```
┌──────────────────────────────────┐
│[<] Solo_Leveling_Ch12  [↩][↪][⇪] │ TopBar ramping (Export dikunci saat busy)
├──────────────────────────────────┤
│[Pan][Move][Sel][Lasso][Brush]…   │ ToolRail LazyRow (fling, key=tool)
│[Bubble][OCR][Inpaint][Clean(3)]  │ AiToolRow LazyRow + Clean, disabled saat busy
│Clean 2/5… 40% ━━━━━●──────       │ busyLabel + LinearProgress determinat
├──────────────────────────────────┤
│┌────────────────────────────────┐│
││   solid #23242F (no checker)   ││ Canvas dominan (weight 1f)
││   ┌─────────┐                  ││ bubble hijau + OCR cyan + lasso magenta
││   │ "Jangan │ ← Text layer     ││ brush stroke di atas, pinch hanya PAN/MOVE
││   │ pergi!" │   stroke+shadow  ││
│└────────────────────────────────┘│
│[Brush|Eraser] [Edit text]        │ quick toggle (disabled saat busy)
├──────────────────────────────────┤
│ ═══ BottomSheet peek 96dp ═══    │ drag ke atas, max 340dp
│ [+Img][+Txt][+Folder][Dup][Del]  │ Layer actions
│ ● Background      [👁][slider]   │ List max 180dp (LazyColumn keyed)
│ ● Clean x3        [👁][slider]   │
│ ● Palette (LazyRow) + Size       │ ColorPanel ring seleksi
└──────────────────────────────────┘
```
- Hirarki: canvas dominan, 1 primary move per area (Export di TopBar, Import di Home).
- Edge-to-edge + IME `adjustResize` agar keyboard tidak menutup text edit.
- Tall-image: tidak load full; viewport Coil Fit + `decodeSampled` 1600/2400 anti-OOM.
- Semua tool target ≥48dp, label survive translasi.
- Stabil: `activeText` via `remember(layers, activeId)`, `AsyncImage` Fit, empty state bila `sourceUri==null`.

## 3. Settings — 3 kartu (rev2)
```
┌─────────────────────────┐
│ [<] Settings            │
├─────────────────────────┤
│ ┌ Terjemahan ─────────┐ │
│ │ info enkripsi       │ │
│ │ [Agnes …] [👁]      │ │
│ │ [Gemini …] [👁]     │ │
│ │ [Sumopod …]         │ │
│ └─────────────────────┘ │
│ ┌ Inpaint & Models ───┐ │
│ │ Telea built-in…     │ │
│ │ [Inpaint Models]    │ │
│ │ [Cek status model]  │ │
│ └─────────────────────┘ │
│ ┌ Tentang ────────────┐ │
│ │ alur 6 langkah      │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

## 4. Editor rev3 — clip + zoom ctrl + wheel + efek pro

| Masalah | Perbaikan rev3 | File |
|---|---|---|
| Canvas zoom menutupi rails atas | Rails `Surface + zIndex(2f)`, canvas `graphicsLayer(clip=true)` | `EditorScreen.kt`, `ToolRail.kt` |
| Seret keset | PAN hanya canvas (MOVE = geser layer via `nudgeActiveLive/Commit`), zoom 0.5–5x + clamp + tombol +/−/reset + badge `%` | `EditorScreen.kt`, `LayerManager.nudgeOffset()` |
| Teks tak muncul | Overlay Box terpusat + offset fraksi, tap-to-select, auto-shadow, stroke 8-arah; TEXT tap = tambah di titik | `EditorScreen.kt` |
| Palette-only membosankan | `VastColorWheel` global (hue ring + SV + alpha + hex) via `WheelColorField` di brush + semua warna teks/efek | `ColorWheel.kt`, `ColorPanel.kt`, `TextEditorDialog.kt` |
| Outline/shadow hanya solid | Mode Solid/Gradasi 2 warna di `Stroke.gradient` + `DropShadow.gradient` (+ offset X/Y shadow dibuka) | `TextStyle.kt`, `TextStyleJson.kt`, render 3 jalur |
| Tanpa blur | `TextEffect.Blur` 0–25px (RenderEffect API 31+, fallback halus; export via BlurMaskFilter) | `TextStyle.kt`, `TextEditorDialog.kt`, `EditorScreen.kt`, `EditorViewModel.kt` |
| Seleksi murahan | Dim luar + double-border + handle + grid thirds crop + glow lasso + info bar | `EditorScreen.kt` |
| Panel tak berguna | Tab Layers / Brush & Warna, peek 112dp, max 430dp | `EditorScreen.kt`, `LayerSheet.kt` |

```
┌──────────────────────────────────┐
│[<] Solo_Leveling_Ch12  [↩][↪][⇪] │ TopBar ramping
├──────────────────────────────────┤
│CANVAS TOOLS                      │ label seksi (Surface, zIndex 2)
│[Pan][Move][Sel][Lasso][Brush]…   │ chip premium 20dp
│AI TOOLS                          │
│[Bubble][OCR][Inpaint][Clean(3)]  │ badge count
├──────────────────────────────────┤
│┌────────────────────────────────┐│
││100% • seret/zoom canvas        ││ badge (tak ikut zoom)
││   ╭─────────╮                  ││ bubble double-border
││   │ "Jangan │ ← border seleksi ││ text tap-to-select
││   │ pergi!" │                  ││
││   ╰ - - - - ╯  ○ handle       ││ select dashed + dim
││                         [+]    ││ zoom ctrl (tak ikut zoom)
││                         [−]    ││
│└────────────────────────────────┘│ clip: tak menutup rails
│[Brush|Eraser] [Edit text]        │
│Select aktif • 1 bubble           │ info bar
├──────────────────────────────────┤
│ ═══ BottomSheet peek 112dp ═══   │ max 430dp
│ [Layers (3)] [Brush & Warna]     │ tab berguna
│ ✎ Title — Teks — 100%  [👁]      │ baris tipe+opacity
│ Wheel: ◉ hue+SV + Alpha + Hex    │ color wheel global
└──────────────────────────────────┘
```

## 5. Text editor — ala Photoshop Character/Paragraph (rev3 + wheel + blur)

Dialog **Edit Text**: konten (`\n` = baris, baris kosong = paragraf) → Font →
Ukuran → Warna (wheel) → Paragraf (Kiri/Tengah/Kanan/Rata) → Leading (jarak baris,
untuk teks 2 baris ke atas) → Tracking (jarak huruf) → Word spacing (jarak kata)
→ Paragraph spacing (jeda tiap paragraf) → Bold/Italic/Caps/U/S →
6 efek (Outline + Shadow + Glow + Gradient + Background + Blur) → Save/Load style.

- Outline & Shadow punya MODE Solid/Gradasi 2 warna; Shadow offset X/Y dibuka
  (dulu terkunci 0/4), blur 0–32.
- Blur 0–25px (penuh Android 12+, fallback halus di bawahnya).
- Semua warna via color wheel (hue ring + SV + alpha + hex + preset).

Render seragam di 3 jalur: preview dialog, canvas (`StackedText` berlapis +
kolom paragraf), font custom (`TextView`: justification + `ScaleXSpan` word-gap;
paragraph-spacing fallback ke leading; outline/gradasi/blur selalu via Compose),
export (`Paint` per-kata + justify penuh + stroke/shadow/gradient/blur/bg).

## 6. Alur (user journey, rev2)
Import (Picker/SAF → Room via `ImportManager`) → Home card (thumbnail) → Editor
(tool LazyRow → Bubble → **Clean (auto per-bubble, progress)** → OCR → Inpaint →
Translate → **Render ke bubble (auto-fit)** → Export custom name/res) → share via FileProvider.
Guard `isBusy` di tiap langkah AI + inferensi di `Dispatchers.Default` = tidak hang.
