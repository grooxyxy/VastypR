# VastypR — NEXT STEPS (file lanjutan sesi)

> File ini dibuat agar sesi berikutnya bisa lanjut tanpa kehilangan konteks.
> Terakhir update: REV3 SELESAI 2026-09-11 (icon regenerasi 15 vector + teks Photoshop + model tanpa-bundle kecuali bubble + tautan resmi). Tanpa download oleh saya. Model manual/download via tombol oleh user.

## 1. Status sekarang

**SELESAI (sudah tertulis di disk, terverifikasi via `ls` + `grep`):**
- `data/prefs/UserPrefs.kt` — ditambah key `INPAINT_BACKEND`, `LAMA_URL`, `MIGAN_URL` + flow/setter.
- `editor/model/TextStyle.kt` — ditambah field `fontId: String?` (null = font sistem).
- `editor/engine/LayerManager.kt` — ditambah `updateText(layers, id, content, style)` + import `VastTextStyle`.
- `editor/model/TextStyleJson.kt` — BARU. Encode/decode `VastTextStyle` via `org.json` (tanpa dep baru).
- `ml/models/InpaintModel.kt` — BARU. `InpaintBackend` (TELEA/LAMA/MIGAN) + `INPAINT_CATALOG` (LaMa ~150–250MB, MiGAN ~10–40MB).
- `ml/models/ModelManager.kt` — BARU. Download OkHttp + progress/cancel/delete/HEAD-size, dir `filesDir/models`. URL dari user, tidak ada URL bawaan.
- `ui/models/ModelViewModel.kt` + `ui/models/ModelScreen.kt` — BARU. UI pilih backend + download/hapus + isi URL Release.
- `data/fonts/FontManager.kt` — BARU. Import TTF/OTF via SAF → `filesDir/fonts`, `LruCache(8)`, preload, `getTypeface()` sinkron.
- `ui/components/FontPicker.kt` — BARU. Preview real via `AndroidView(TextView)`, search, import, delete.
- `ui/editor/EditorViewModel.kt` — REWRITE. Inject `FontManager` + `UserPrefs`; tambah `fontList`, `savedStyle`, `updateActiveText()`, `saveTextStyle()`, `getSavedStyle()`, `fontTypeface()`, `importFont()`, `deleteFont()`, `activeTextLayer()`.
- `ui/editor/components/TextEditorDialog.kt` — BARU. Dialog edit teks lengkap: konten, `FontPicker`, size, warna, bold/italic/center, 5 efek (stroke/shadow/glow/gradient/background), Save/Load style.
- `ui/editor/EditorScreen.kt` — SELESAI wiring: `showTextEditor` + `fontLauncher` (SAF OpenDocument ttf/otf) + `LayerText` (AndroidView TextView bila font custom & efek kompatibel, else StackedText) + `StackedText(text, style)` (fontSizeSp/weight/style/align, mapping fontId sistem, gradient Brush, glow copy ekstra) + tombol "Edit text" + host `TextEditorDialog` + param `onOpenModels` (nullable, fallback toast).
- `navigation/VastypRRoute.kt` — tambah `data object Models`.
- `MainActivity.kt` — branch `Models -> ModelScreen`, `modelsReturn` agar Back dari Models kembali ke Editor bila dibuka via Inpaint.
- `ui/settings/SettingsScreen.kt` — tiap API key ada toggle show/hide, tombol "Inpaint Models (LaMa / MiGAN)" → `onOpenModels`, teks penjelas `filesDir/models`.
- `README.md` — seksi Models / Fonts / Text Editor.
- Step 4 (Export + Translate wiring, SELESAI):
  - `ui/editor/components/ExportDialog.kt` — BARU. Format jpeg/png/webp + nama custom + long-side (Asli/2048/1600/1080) + quality, ingat format via `UserPrefs`.
  - `ui/editor/components/TranslateDialog.kt` — BARU. Provider Agnes/Gemini/Sumopod + target id/en/ja/ko/zh + preview hasil + Pakai. Network via OkHttp yang sudah ada.
  - `ui/editor/EditorViewModel.kt` — inject `ExportManager` + 3 provider `@Named`; `exportCurrent()` (render android.graphics murni: bg + text layers, `isBusy`, persist format, `ExportDone`), `translatePreview()` (suspend Result), `applyTranslation()`, `lastExportFormat()`.
  - `ui/editor/EditorScreen.kt` — `showExport`/`showTranslate`/`lastFormat`; TopBar Export → dialog; AI Translate → dialog (cek layer teks aktif); host kedua dialog.
- Step 5 (SELESAI, tanpa dep baru, tanpa download):
  - `editor/model/EditorState.kt` — `sourceUri`, `canvasWidth/Height`, `cropRect`, `selectionRect`, `lassoPoints`, `bubbles`, `ocrLines` (semua koordinat NORMALISASI 0..1).
  - `data/local/ProjectDao.kt` — tambah `getById()`.
  - `editor/engine/LayerManager.kt` — tambah `addImageWithPath()` (hasil inpaint → layer).
  - `editor/engine/TallImageManager.kt` — tambah `decodeSampled(uri, maxLongSide)` anti-OOM.
  - `ml/detection/MaskBuilder.kt` — `dilatePx` kini beneran didilasi (stroke tebal, tanpa OpenCV).
  - `translation/TranslationParsers.kt` — BARU (org.json bawaan): `gemini()` + `generic()`; di-wire ke 3 translator.
  - `ui/editor/components/ToolRail.kt` — chip Lasso + icon `ic_tool_lasso.xml` BARU.
  - Export full-fidelity: sumber sampled + image layers + brush (paint/erase) + teks multi-line + crop.
  - Canvas: gambar sumber via Coil (Fit), image layers ber-file, overlay bubble/ocr/seleksi/lasso/crop, gesture CROP/SELECT_RECT/SELECT_LASSO, progress `isBusy`, baris Reset/Clear kontekstual.
- MVP-2 wiring (SELESAI kode; dep gradle DIAKTIFKAN atas izin user — download dep terjadi di CI, bukan lokal; model .onnx copy MANUAL dari Manhwa-Translator/model):
  - `app/build.gradle.kts` — `onnxruntime-mobile` + `mlkit-text-recognition` uncomment; `proguard-rules.pro` + keep ORT.
  - `ml/models/ModelFiles.kt` — `dir/file/exists/require()` + `requireFirst()` + `MissingModelException` (pesan ramah: copy manual).
  - `ml/detection/BubbleDetector.kt` + `OcrEngine.kt` — kontrak berbasis Bitmap (caller atur sampling; koordinat px input).
  - `ml/detection/YoloV8mBubbleDetector.kt` — selaras referensi (lihat COPY di bawah).
  - `ml/detection/MlKitOcrEngine.kt` — Latin offline, region-crop + offset balik, tanpa dep play-services tambahan.
  - `ml/inpaint/DiffusionInpainter.kt` — "Telea-lite" murni Kotlin (OpenCV ditolak: modul ~100MB), backend default `telea`.
  - `ml/inpaint/OnnxInpaintRunner.kt` — runner generik 2-input + tile-512 per bbox; dipakai LaMa (`lama_fp32.onnx`).
  - `di/AppModule.kt` — binding detector/OCR/TextMask/3 inpainter (@Named telea/lama/migan).
  - `EditorViewModel` — `loadProject()`, `detectBubbles()` (sisi-2400), `runOcr()` (region seleksi/lasso), `runInpaint()` (mask manual > PP-OCR otomatis → layer baru), mapping Fit.
  - `EditorScreen` — tombol Bubble/OCR/Inpaint aktif; `loadProject(projectId)`.
- COPY dari Manhwa-Translator (SELESAI, tanpa download — baca lokal + inspeksi biner Node):
  - Layout TERVERIFIKASI dari biner (bukan tebakan): bubble `images[1,3,640,640]` → `output0[1,6,8400]`; backup `model.onnx` → `[1,5,8400]`; PP-OCR det in dinamis `[N,3,H,W]` → 1 prob-map.
  - `YoloV8mBubbleDetector.kt` REWRITE selaras `detect_bubbles.py`+`core.py`: pad 114, round+offset int, SKOR MENTAH (5kol langsung, ≥6kol argmax; core.py tak filter class), conf 0.30, NMS 0.45, tiling 1200/300 + NMS global, session dibuka sekali per detect. Kandidat file: `comic-speech-bubble-detector.onnx` lalu `bubble_yolov8m.onnx` (`ModelFiles.requireFirst` BARU).
  - Quirk transpose Python dilewati dengan sengaja — Kotlin pakai layout terverifikasi langsung.
  - `ml/detection/TextMaskProvider.kt` + `PpOcrTextMask.kt` BARU: port `local_text_mask.py` (limit-960 mult32, norm ImageNet, sigmoid-bila-perlu, thresh 0.3, komponen 8-konektivitas, UNCLIP PER-KOMPONEN via dilasi lokal, buang >40%, tighten open/close+fringe 2px, NEAREST upscale). Tanpa pyclipper/OpenCV.
  - `ml/inpaint/MiganInpainter.kt` BARU: port persis `migan_inpaint.py` (input 4ch `[mask-0.5,R*m,G*m,B*m]` 512, konvensi 255=KNOWN, feather dilate-3x3+Gauss-5x5-s1.0 + blend, fallback Diffusion). File `migan_lxfater.onnx` (nama disamakan; katalog `InpaintModel` + AppModule updated).
  - `EditorViewModel`: deteksi di sisi-2400 (tiling aktif), `runInpaint` mask manual > PP-OCR otomatis (region=bbox bubble, else full).
  - `di/AppModule.kt`: binding `TextMaskProvider`, migan → `MiganInpainter`.

**REV2 FLUID+STABIL 2026-09-11 (tanpa download, tanpa dep baru):**
- `editor/model/EditorState.kt` — tambah `busyLabel: String?` + `busyProgress: Float?` (progress determinat Clean).
- `ui/editor/EditorViewModel.kt` — guard `isBusy` di detect/ocr/inpaint/clean/export; inferensi di `Dispatchers.Default`; helper `setBusy/clearBusy/activeInpainter`; BARU `cleanAllBubbles()` (auto-detect bila kosong, crop→mask PP-OCR fallback rect→inpaint→paste-back per bubble, progress per-bubble, `cacheDir/clean_*.png` + layer) + `renderTranslationToBubble()` (Layer.Text baru di tengah bubble/selection, font auto-fit 14..52sp).
- `ui/editor/components/ToolRail.kt` — REWRITE LazyRow keyed + `enabled` + tombol Clean + badge count.
- `ui/editor/EditorScreen.kt` — REWRITE fluid: checkerboard 900-rect DIHAPUS→solid bg; gesture dipisah (transformable hanya PAN/MOVE); AI dikunci saat busy; progress determinat; `sheetPeekHeight=96dp` + sheet max 340dp; `activeText` remember; empty state sourceUri null; wire Clean + Render-ke-bubble.
- `ui/editor/components/LayerSheet.kt` — list `heightIn(max=180dp)` + ellipsis.
- `ui/editor/components/ColorPanel.kt` — LazyRow + ring seleksi.
- `ui/home/HomeViewModel.kt` — inject `ImportManager`; tambah `importUri/deleteProject/clearError` (FAB dulu mati kini hidup).
- `ui/home/HomeScreen.kt` — REWRITE: Photo Picker + SAF fallback, thumbnail Coil + delete, loading spinner + snackbar error + empty 2 tombol.
- `ui/settings/SettingsScreen.kt` — 3 kartu sectioned (Terjemahan/Inpaint/Tentang).
- `ui/editor/components/TranslateDialog.kt` — tambah `onRenderToBubble` + `hasBubbleTarget` (P2).
- `docs/mockup-{editor,home,settings}.svg` rev2 + `docs/ui-ux.md` rev2 (tabel rev1→rev2).
- `NEXT_STEP.md` — P1+P2 dicentang; sisa = Push CI + tes APK.

**REV3 2026-09-11 (tanpa download oleh saya, tanpa dep baru):**
- Icon: 14 `ic_tool_*.xml` diregenerasi outline 1.8dp konsisten + tint tema (semua sudah `<vector>` sejak awal; kini gaya seragam) + BARU `ic_tool_clean.xml` (tombol Clean pakai ini). `drawable` kini 16 file.
- Teks Photoshop: `VastAlign` LEFT/CENTER/RIGHT/JUSTIFY (ganti `alignCenter`; JSON lama tetap dibaca); BARU `lineHeightEm/letterSpacingEm/wordSpacingEm/paragraphSpacingEm/allCaps/underline/strike` di `VastTextStyle` + `TextStyleJson` + `TextTypography.kt` (helper `wordSpaced/composeAlign/...`) + `TextEditorDialog` (4 seksi baru) + render `StackedText`/`TextView`/export (justify + per-kata).
- Model: `MODEL_CATALOG` 4 entri + defaultUrl resmi (PP-OCR `PaddlePaddle/PP-OCRv6_small_det_onnx`, MiGAN `lxfater/inpaint-web`, LaMa `Carve/LaMa-ONNX` fp32) + `bundled=true` bubble; `UserPrefs` BUBBLE_URL/PPOCR_URL; `ModelManager` generik (flow per modelId, kompat `lama/migan`); `ModelFiles.requireFirstOrBundled` + `YoloV8mBubbleDetector` pakai itu; gradle task `bundleBubbleModel` + step CI `BUBBLE_MODEL_URL` + `local.properties.example`; `ModelScreen/ModelViewModel` 3 seksi + Reset URL; `SettingsScreen` teks Models diperbarui; `README` seksi model + text editor ditulis ulang.

**BELUM (saran lanjut, tetap tanpa download/build lokal tanpa izin):**

- MVP-2 sisa: PP-OCR REC (baca tulis CJK — Manhwa-Translator pakai Gemini-vision; di VastypR opsional karena ML Kit Latin + provider translate sudah ada), eyedrop sampling pixel tile, OCR CJK offline (butuh artefak ML Kit tambahan = DEP BARU, tanya user dulu), session ORT persisten.
- MVP-3: LaMa overlap-blend penuh, folder drag-reorder, gradient/glow penuh native.
- Verifikasi 2026-09-11: push ke `github.com/grooxyxy/VastypR` → **CI HIJAU** (run 34552745664, APK 44.5MB). Fix dari log: import java.io/net, ORT 1.18.0, `<circle>`→path, hilt-navigation-compose 1.2.0, toArgb import, copy(brush), isUnderlineText, lint justification + backup rules. Sisa: tes di HP.

## 2. Aturan aktif (JANGAN dilanggar)
- **DILARANG download model/dependencies tanpa izin user.** Izin yang pernah diberikan: download+install `git` via `apk` (SELESAI, git 2.47.3). Gradle deps, ONNX, font, model = BELUM diizinkan.
- Build hanya di GitHub Actions (`.github/workflows/android-ci.yml`), bukan lokal.
- Package: `com.volxsy.vastypr` (lowercase, normalisasi dari `com.volxsy.VastypR`).
- Semua dep baru DILARANG — pakai yang ada: Compose BOM 2024.10.00, `org.json` (built-in), OkHttp (sudah ada), `AndroidView` (compose-ui), `OpenDocument` (activity artifact transitif).

## 3. Fakta environment
- Workdir: `/public`; project: `/public/VastypR` (~500KB); skills: `/public/android-agent-skills` (34 skill, jangan dihapus).
- Tools: `git 2.47.3` ✅, `node v22`, `wget`, busybox (`grep` tanpa `--include`). TIDAK ada: JDK/SDK, python3, curl.
- Push GitHub: git siap, tapi butuh URL repo kosong + PAT dari user. Alternatif: upload manual via web / script node API (0MB).

## 4. Cara lanjutkan sesi (copy-paste prompt ini)
```
Lanjut VastypR dari NEXT_STEPS.md. Patuhi aturan: tanpa download apapun, tanpa dep baru, tanpa build lokal.
```
