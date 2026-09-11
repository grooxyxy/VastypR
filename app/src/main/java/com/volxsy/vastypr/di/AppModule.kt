package com.volxsy.vastypr.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.volxsy.vastypr.data.local.VastypRDatabase
import com.volxsy.vastypr.data.prefs.UserPrefs
import com.volxsy.vastypr.data.security.ApiKeyStore
import com.volxsy.vastypr.ml.detection.BubbleDetector
import com.volxsy.vastypr.ml.detection.MlKitOcrEngine
import com.volxsy.vastypr.ml.detection.OcrEngine
import com.volxsy.vastypr.ml.detection.PpOcrTextMask
import com.volxsy.vastypr.ml.detection.TextMaskProvider
import com.volxsy.vastypr.ml.detection.YoloV8mBubbleDetector
import com.volxsy.vastypr.ml.inpaint.DiffusionInpainter
import com.volxsy.vastypr.ml.inpaint.Inpainter
import com.volxsy.vastypr.ml.inpaint.MiganInpainter
import com.volxsy.vastypr.ml.inpaint.OnnxInpaintRunner
import com.volxsy.vastypr.ml.inpaint.OnnxInpaintSpec
import com.volxsy.vastypr.ml.models.ModelFiles
import com.volxsy.vastypr.translation.AgnesTranslator
import com.volxsy.vastypr.translation.GeminiTranslator
import com.volxsy.vastypr.translation.SumopodTranslator
import com.volxsy.vastypr.translation.TranslationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

// Skill: android-di-hilt + android-room-database + android-local-persistence-datastore
// + android-security-best-practices + android-networking-retrofit-okhttp
private val Context.dataStore by preferencesDataStore(name = "vastypr_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): VastypRDatabase =
        Room.databaseBuilder(ctx, VastypRDatabase::class.java, "vastypr.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideProjectDao(db: VastypRDatabase) = db.projectDao()

    @Provides @Singleton
    fun provideUserPrefs(@ApplicationContext ctx: Context): UserPrefs =
        UserPrefs(ctx.dataStore)

    @Provides @Singleton
    fun provideApiKeyStore(@ApplicationContext ctx: Context): ApiKeyStore =
        ApiKeyStore(ctx)

    // Translation providers — skill: android-networking-retrofit-okhttp
    // Masing-masing butuh API key dari ApiKeyStore (diinput user di Settings).
    @Provides @Singleton @Named("agnes")
    fun provideAgnes(apiKeyStore: ApiKeyStore): TranslationProvider =
        AgnesTranslator(apiKeyStore)

    @Provides @Singleton @Named("gemini")
    fun provideGemini(apiKeyStore: ApiKeyStore): TranslationProvider =
        GeminiTranslator(apiKeyStore)

    @Provides @Singleton @Named("sumopod")
    fun provideSumopod(apiKeyStore: ApiKeyStore): TranslationProvider =
        SumopodTranslator(apiKeyStore)

    // ---- MVP-2 ML (model .onnx manual user, dep aktif atas izin user) ----
    // Bubble YOLOv8: filesDir/models/comic-speech-bubble-detector.onnx (copy
    // manual dari Manhwa-Translator/model, fallback nama lama bubble_yolov8m.onnx).
    @Provides @Singleton
    fun provideBubbleDetector(models: ModelFiles): BubbleDetector =
        YoloV8mBubbleDetector(models)

    // OCR cepat offline (Latin). CJK = artefak terpisah = dep baru -> ditahan.
    @Provides @Singleton
    fun provideOcrEngine(): OcrEngine = MlKitOcrEngine()

    // Mask teks presisi PP-OCRv6 det (copy manual PP-OCRv6_small_det.onnx).
    @Provides @Singleton
    fun provideTextMask(models: ModelFiles): TextMaskProvider =
        PpOcrTextMask(models)

    // Telea-lite murni Kotlin (tanpa OpenCV) = backend default "telea".
    @Provides @Singleton @Named("telea")
    fun provideTeleaInpainter(): Inpainter = DiffusionInpainter()

    // LaMa neural (tile 512): filesDir/models/lama_fp32.onnx (manual).
    @Provides @Singleton @Named("lama")
    fun provideLamaInpainter(models: ModelFiles): Inpainter =
        OnnxInpaintRunner(models, OnnxInpaintSpec("lama_fp32.onnx", "LaMa"))

    // MiGAN ringan (pipeline 4ch persis migan_inpaint.py): filesDir/models/migan_lxfater.onnx.
    @Provides @Singleton @Named("migan")
    fun provideMiganInpainter(models: ModelFiles): Inpainter =
        MiganInpainter(models)
}
