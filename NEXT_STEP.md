# VastypR — NEXT STEP (rencana kerja berikutnya)

> Dibuat: 2026-09-08. Rev2 UI/UX fluid+stabil: 2026-09-11. Rev3 icon+teks+model: 2026-09-11. Konteks penuh ada di `NEXT_STEPS.md`.
> Aturan tetap: **tanpa download model/dependencies oleh saya** — model `.onnx` copy MANUAL / download via tombol Settings → Models oleh user.

## Status sekarang (1 baris)

Bubble YOLO + PP-OCR mask + MiGAN/Telea/LaMa + Export/Translate/Crop/Lasso + **Clean per-bubble + Render-ke-bubble + UI rev2 fluid + icon regenerasi + teks Photoshop + bundle bubble** sudah di kode; **belum diverifikasi compile** (tunggu CI).

## Selesai di rev2 ini (2026-09-11, tanpa download, tanpa dep baru)
- [x] Prioritas 1: `EditorViewModel.cleanAllBubbles()` — loop bubble (auto-detect bila kosong), crop+mask+inpaint+paste-back per bubble, fallback rect bila PP-OCR model belum ada, progress `busyLabel/busyProgress`, hasil `cacheDir/clean_*.png` + 1 layer baru. Tombol "Clean" di `AiToolRow` + badge count.
- [x] Prioritas 2: `EditorViewModel.renderTranslationToBubble()` — Layer.Text baru di tengah bubble pertama (atau selection), font auto-fit `boxW/(0.55*maxLen)` 14..52sp. `TranslateDialog` tambah tombol "Render ke bubble" (+ `hasBubbleTarget`).
- [x] Stabil: guard `isBusy` di detect/ocr/inpaint/clean/export + `enabled=!isBusy` di UI; inferensi di `Dispatchers.Default` (`detector/ocr/mask/inpainter`); helper `setBusy/clearBusy/activeInpainter`; `EditorState.busyLabel/busyProgress`.
- [x] Fluid: checkerboard 900-rect dihapus → solid bg; gesture dipisah (transformable hanya PAN/MOVE); ToolRail/AiToolRow/ColorPanel → LazyRow keyed; BottomSheet peek 96dp + max 340dp, LayerSheet list max 180dp; `activeText` via remember; empty state canvas; Home import beneran (Picker+SAF) + thumbnail + delete + loading/error; Settings 3 kartu.
- [x] Mockup: `docs/mockup-{editor,home,settings}.svg` rev2 + `docs/ui-ux.md` tabel rev1→rev2.

## Selesai di rev3 ini (2026-09-11, tanpa download oleh saya, tanpa dep baru)

- [x] Icon: SEMUA sudah `<vector>` (15 file + launcher). 14 tool diregenerasi gaya outline konsisten 1.8dp + `?attr/colorControlNormal`, tambah `ic_tool_clean.xml` (dipakai tombol Clean).
- [x] Teks Photoshop: `VastAlign` (Kiri/Tengah/Kanan/Rata) ganti `alignCenter`; BARU Leading, Tracking, Word spacing, Paragraph spacing (em), AllCaps/Underline/Strike. Berlaku di preview dialog + canvas Compose (`TextTypography.wordSpaced`) + font custom (`TextView`) + export (`Paint` per-kata + justify). Style lama tetap kebaca (JSON legacy).
- [x] Model tanpa bundle (kecuali bubble): katalog 4 model + tautan default resmi (PP-OCR PaddlePaddle, MiGAN lxfater, LaMa Carve fp32) — bisa dioverride; `ModelManager` generik + `UserPrefs` URL bubble/ppocr; layar Models 3 seksi + Reset URL.
- [x] Bundle bubble: `ModelFiles.requireFirstOrBundled` (assets→filesDir sekali salin) + task gradle `bundleBubbleModel` (`-PBUBBLE_MODEL_PATH` / `-PBUBBLE_MODEL_URL`) + step CI opsional via secret + `local.properties.example`.

## Prioritas berikutnya: Push GitHub + verifikasi CI

- [ ] Push isi `VastypR/` ke repo kosong (tanpa file `.onnx`!) → Actions **VastypR CI** hijau → download APK → tes di HP: Bubble, Clean, OCR, Inpaint (Telea), Render, Export.
- [ ] Bila CI merah → kirim log error ke saya (khususnya area `ai.onnxruntime` / ML Kit / `PickVisualMedia` / `LinearProgressIndicator(progress={})`).
- Selesai bila: APK terinstall dan 6 tombol AI jalan dengan model manual.

## Checklist model (kamu, bukan saya — atau via tombol Download di Settings → Models)

- [ ] Bubble: bundle otomatis bila build pakai `BUBBLE_MODEL_PATH`/`BUBBLE_MODEL_URL`; else file manual / Download.
- [ ] PP-OCR / MiGAN / LaMa: tekan Download di Settings → Models (tautan resmi sudah terisi), atau copy manual.

## Ditahan (butuh izin kamu dulu)

- OCR CJK offline (artefak ML Kit tambahan = dep baru).
- Dependensi baru apapun / download model oleh saya.
