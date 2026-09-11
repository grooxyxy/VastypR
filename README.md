# VastypR — Manhwa & Manga Editor (native Kotlin + Compose)

App: **VastypR** · package `com.volxsy.vastypr` (dinormalisasi lowercase dari `com.volxsy.VastypR` agar sesuai Play Store convention) · minSdk **26 (Android 8)** · targetSdk 34.

> Dibuat di sini sebagai source saja (tidak di-build lokal). Build resmi di **GitHub Actions** (lihat `.github/workflows/android-ci.yml`).

## Fitur (status MVP-1 ✅ / MVP-2+ 🚧)

MVP-1 (sudah jadi di repo ini):
- Import all image (Photo Picker + SAF + share-sheet `SEND`/`SEND_MULTIPLE`), export jpeg/png/webp + custom name + custom resolution (`ExportManager`)
- Layer penuh: add / delete / duplicate / copy / clip / folder / visibility / opacity / reorder (`LayerManager`)
- Undo / Redo 100 langkah (`UndoRedoManager`)
- Tools: pan (zoom 0.25–10x), move, selection-rect, **lasso**, brush + eraser, eyedrop (panel + pipet dari gambar), text, **crop interaktif non-destruktif**
- Brush + color wheel + eyedrop + size slider (`BrushEngine`, `ColorPanel`)
- Teks: auto-fit ke lebar canvas saat dibuat, tombol **Fit ke bubble** + **Pusatkan** (ala TypeR), style manager bernama (simpan/duplikat/hapus/terapkan)
- Canvas tall-image safe: viewport pan/zoom + `TallImageManager.decodeRegion` (720x16000+ tanpa OOM) + gambar sumber via Coil (Fit)
- Text + efek ditumpuk: outline + shadow (mode solid/gradasi) + glow + gradient + background + blur (`TextStyle.kt`)
- Text style save/load via DataStore (`UserPrefs`)
- API key screen terenkripsi: Agnes AI, Gemini AI, Sumopod (`ApiKeyStore`)
- Icon adaptive clean (bubble + V + brush dot)
- Clean UI dark-first Material3 (Home → Editor → Settings)

MVP-2 (kode wiring SELESAI; dep gradle aktif; model .onnx copy MANUAL dari Manhwa-Translator/model):
- Bubble detection YOLOv8m (`YoloV8mBubbleDetector`, onnxruntime-android FULL, `models/comic-speech-bubble-detector.onnx`, strip-tiling tall + NMS global)
- OCR: ML Kit v2 Latin offline (`MlKitOcrEngine`); mask teks presisi PP-OCRv6 det (`PpOcrTextMask`, `models/PP-OCRv6_small_det.onnx`)
- Mask polygon sesuai bentuk teks (`MaskBuilder` + dilasi) + unclip per-komponen PP-OCR
- Inpaint Telea-lite murni Kotlin tanpa OpenCV (default) + LaMa ONNX + MiGAN persis pipeline `migan_inpaint.py` (`models/migan_lxfater.onnx`)
- Export full-fidelity (sumber + layer + brush + teks + crop), parsing JSON translate (Gemini/umum)

MVP-3:
- LaMa inpaint (`models/lama_fp32.onnx`, tile 512)
- Translation penuh + crop penuh + lasso + gradient/glow penuh + folder drag-reorder

## Skill yang dipakai per tugas (sesuai repo android-agent-skills)

| Tugas | Skill |
|---|---|
| Gradle, version catalog, toolchain JDK17, desugaring API 26 | `android-gradle-build-logic` |
| Kotlin idioms, sealed, null-safety | `android-kotlin-core` |
| Clean architecture, ViewModel tipis, pure domain | `android-architecture-clean` |
| Modul tunggal MVP-1, split nanti | `android-modularization` |
| DI Hilt | `android-di-hilt` |
| Coroutines/Flow, StateFlow/SharedFlow | `android-coroutines-flow` |
| UiState immutable + events | `android-state-management` |
| Compose layout, Canvas, BottomSheet | `android-compose-foundations` |
| State/effects lifecycle-safe | `android-compose-state-effects` |
| Material3 tokens, dark theme | `android-material3-design-system` |
| Layar Home/Editor/Settings dari nol | `android-mobile-frontend-design` |
| A11y semantics, touch target | `android-compose-accessibility` |
| Tall-image perf, largeHeap + tiling | `android-compose-performance` |
| Import/export, FileProvider, MediaStore | `android-media-files-sharing` |
| Permission minimal (Photo Picker first) | `android-permissions-activity-results` |
| Room projects, DataStore prefs | `android-room-database`, `android-local-persistence-datastore` |
| Retrofit/OkHttp translators | `android-networking-retrofit-okhttp` |
| EncryptedSharedPreferences, backup rules, exported eksplisit | `android-security-best-practices` |
| Unit test reducer/manager | `android-testing-unit` |
| CI GitHub APK | `android-ci-cd-release-playstore` |

## Cara build di GitHub (untuk pemula)

1. Buat repo baru di GitHub (mis. `VastypR`), **jangan** centang add README.
2. Upload isi folder `VastypR/` ini ke repo:
   - via web: Add file → Upload files, drag semua isi `VastypR/`
   - atau via git (di PC yang ada git):
     ```
     cd VastypR
     git init -b main
     git add .
     git commit -m "VastypR MVP-1"
     git remote add origin https://github.com/USERNAME/VastypR.git
     git push -u origin main
     ```
3. Buka tab **Actions** → jalankan **VastypR CI** → tunggu hijau → download **vastypr-debug-apk** dari Artifacts.
4. Install APK di HP Android 8+ untuk tes.

Tidak perlu install Android Studio untuk build pertama. Kalau mau edit code, pakai Android Studio Ladybug+ dan buka folder `VastypR/`.

## Model: bubble dibundle, lainnya download manual (tanpa bundle ke APK)

**Bubble detector DIBUNDLE di APK saat build** — file `.onnx` TIDAK di-commit
(99MB melebihi batas GitHub). Sumber model (prioritas: path lokal > URL > default):

- Default otomatis: Google Drive milik user (link yang diberikan user, harus
  publik "Anyone with the link") — diunduh + dibundle saat build tanpa setting
  apa pun. Unduhan Drive memakai confirm-token + cookie (lihat
  `downloadModelFile` di `app/build.gradle.kts`).
- File lokal: `gradle :app:assembleDebug -PBUBBLE_MODEL_PATH=/lokal/Manhwa-Translator/model/comic-speech-bubble-detector.onnx`
- URL lain (mis. GitHub Release): secret `BUBBLE_MODEL_URL` di repo GitHub,
  atau `-PBUBBLE_MODEL_URL=https://.../bubble.onnx`. Lihat `local.properties.example`.

Deteksi gambar tall (720x16000+): decode per strip vertikal 2000px (+overlap
200px) di resolusi penuh via `BitmapRegionDecoder` + tiling YOLO 1200/300 per
strip + NMS global — tanpa OOM, bubble kecil tidak hancur.

**Model lain TIDAK dibundle** — download MANUAL eksplisit per tombol di
**Settings → Models** (atau copy file via Device Explorer ke
`data/data/com.volxsy.vastypr/files/models/`). Tautan awal resmi sudah terisi,
bisa diganti link GitHub Release sendiri:

| Model di VastypR | Tautan default resmi | Dipakai |
|---|---|---|
| `comic-speech-bubble-detector.onnx` (~99MB, **bundled**) | Google Drive user (default, otomatis saat build) | Bubble YOLO + Clean, strip-tiling 720x16000+ |
| `PP-OCRv6_small_det.onnx` (~10MB) | `huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx` (Apache-2.0) | Mask teks otomatis (tanpa seleksi) |
| `migan_lxfater.onnx` (~30MB) | `huggingface.co/lxfater/inpaint-web/resolve/main/migan.onnx` (GPL-3.0) | Backend MiGAN (gagal → fallback Telea) |
| `lama_fp32.onnx` (~207MB) | `huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx` (Apache-2.0, varian fp32) | Backend LaMa (kualitas terbaik) |
| `model.onnx` (11.7MB, backup lama Manhwa-Translator) | — | Tidak dipakai (arsitektur 1 kelas lama) |

- File tersimpan di `filesDir/models/` (internal, tidak ikut backup). Tidak ada download otomatis.
- Bila file belum ada, tombol AI memberi pesan jelas (tidak crash). Backend inpaint aktif dipilih di Settings (default Telea-lite tanpa model).

## Models (Settings → Models)

- **Deteksi bubble**: status Terinstall/Bundled + kolom link (kosongkan bila APK sudah bundle) + Download.
- **Mask teks (PP-OCR)**: link default resmi + Download (progress + Batal).
- **Backend inpaint**: **Telea** built-in selalu tersedia (default). **LaMa** & **MiGAN**: Download → **Pakai** untuk jadikan backend aktif, ikon tong sampah untuk hapus.

## Fonts (TTF / OTF)

- Buka teks → **Edit text** → seksi **Font (preview + import)**.
- Preview real (bukan nama file), cari via kolom search, **Import** via file picker (SAF) → tersimpan di `filesDir/fonts/`. Font sistem (Default/Sans/Serif/Mono) selalu ada, tidak bisa dihapus.
- Canvas: font custom dirender akurat via `TextView` bila efek kompatibel; bila pakai Stroke/Gradient/Glow → fallback Compose (tetap 5 efek jalan).

## Text Editor (Photoshop-like + 6 efek + save/load)

- Pilih layer teks → **Edit text**: konten (`\n` = baris baru, baris kosong = paragraf), font, size (12–120sp), warna via **color wheel** (hue ring + SV + alpha + hex + preset), Bold/Italic.
- **Paragraf**: Kiri/Tengah/Kanan/Rata (justify).
- **Tipografi (em)**: Leading / jarak baris (0.90–2.50, untuk teks 2 baris ke atas),
  Tracking / jarak huruf (−0.10–0.50), Word spacing / jarak kata (0–1),
  Paragraph spacing / jeda tiap paragraf (0–1.5, aktif pada batas paragraf).
- **Transformasi**: AA Caps, Underline, Strikethrough.
- 6 efek **ditumpuk**: Outline (solid/gradasi), Shadow (solid/gradasi + offset X/Y), Outer Glow, Gradient Fill, Background, Blur (+ Save/Load style via DataStore, kompatibel dengan style lama).
- Render urutan: fill → gradient → stroke → glow → shadow → background → blur. Berlaku di canvas, font custom (`TextView` untuk efek sederhana), dan export PNG/JPEG/WEBP.

## Export (format + nama + resolusi)

- Tombol **Export** (top bar Editor) → dialog: nama file custom, format **JPEG/PNG/WEBP**, sisi panjang (Asli/2048/1600/1080), quality (non-PNG). Format terakhir diingat.
- Tersimpan ke **Pictures/VastypR** (MediaStore, tanpa permission ekstra di API 29+). MVP-4 render background + text layers; image layer & brush full-fidelity di Step 5.

## Translate (Agnes / Gemini / Sumopod)

- Pilih layer teks → tombol **Translate** → pilih provider + target (id/en/ja/ko/zh) → **Translate** untuk preview → **Pakai** untuk terapkan ke layer.
- Butuh internet + API key di **Settings** (tersimpan terenkripsi). Tanpa dep baru (OkHttp yang sudah ada).

## Struktur

```
VastypR/
  settings.gradle.kts  gradle/libs.versions.toml  build.gradle.kts
  app/
    src/main/AndroidManifest.xml
    src/main/java/com/volxsy/vastypr/
      MainActivity.kt  VastypRApp.kt  di/  navigation/
      ui/theme/  ui/home/  ui/editor/ (+components)  ui/settings/
      editor/model/  editor/engine/  editor/tools/  editor/io/
      data/local/  data/prefs/  data/security/
      ml/detection/  ml/inpaint/  translation/
    src/main/res/ (values, xml, drawable icon, mipmap-anydpi-v26)
    src/test/ (LayerManagerTest, UndoRedoManagerTest)
  .github/workflows/android-ci.yml
```
