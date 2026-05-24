package com.nedmah.supertonic_kmp.api

import com.nedmah.supertonic_kmp.download.ModelDownloader
import com.nedmah.supertonic_kmp.internal.InferenceEngine
import com.nedmah.supertonic_kmp.internal.TextChunker
import com.nedmah.supertonic_kmp.internal.VoiceStyleLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Library entry point. Create one instance and reuse.
 *
 * Usage example (Android):
 * ```kotlin
 * val tts = SupertonicTts(
 * SupertonicConfig(storageDir = context.filesDir.path + "/supertonic")
 *)
 *
 * // Download models (~265MB, once)
 * tts.download().collect { state ->
 * when (state) {
 * is DownloadState.Downloading -> showProgress(state.fraction)
 * is DownloadState.Ready -> enableButton()
 * is DownloadState.Error -> showError(state.cause)
 * else -> {}
 * }
 * }
 *
 * // Generate WAV
 * val result = tts.generate("Hello, world!", lang = "ru")
 * if (result is GenerateResult.Success) {
 * playWav(result.wav) // Your player
 * }
 *
 * // Free up resources
 * tts.close()
 * ```
 */
class SupertonicTts(private val config: SupertonicConfig) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engine = InferenceEngine(config)
    private val downloader = ModelDownloader(config.huggingFaceBaseUrl, config.storageDir)
    private val voiceStyleLoader = VoiceStyleLoader()

    private val _downloadState = MutableStateFlow(
        if (downloader.isComplete(SupertonicConfig.MODEL_FILES))
            DownloadState.Ready
        else
            DownloadState.NotDownloaded
    )

    /**
     * Current model download status.
     * Subscribe from the ViewModel to update the UI.
     */
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Download ONNX models (~265MB).
     *
     * - Cancel via coroutine cancellation
     * - Safe to retry - already downloaded files are skipped
     * - After [com.nedmah.supertonic_kmp.api.DownloadState.Ready], the engine is automatically initialized
     */
    fun download(): Flow<DownloadState> =
        downloader.download(SupertonicConfig.MODEL_FILES)
            .onEach { state ->
                _downloadState.value = state
                if (state is DownloadState.Ready) {
                    initEngineIfNeeded()
                }
            }

    /**
     * Delete downloaded models from disk.
     * After calling [downloadState], it will return to [com.nedmah.supertonic_kmp.api.DownloadState.NotDownloaded].
     */
    fun deleteModel() {
        engine.close()
        downloader.deleteAll()
        _downloadState.value = DownloadState.NotDownloaded
    }

    /**
     * Generate WAV audio from text.
     *
     * @return [com.nedmah.supertonic_kmp.api.GenerateResult.Success] with WAV bytes (44100Hz, 16-bit, mono)
     * or [com.nedmah.supertonic_kmp.api.GenerateResult.Error] if the engine is not ready or an error occurred
     */
    suspend fun generate(
        text: String,
        lang: String = config.defaultLang,
        voice: SupertonicVoice = config.defaultVoice,
        speed: Float = config.defaultSpeed,
    ): GenerateResult = withContext(Dispatchers.Default) {
        if (_downloadState.value !is DownloadState.Ready) {
            return@withContext GenerateResult.Error(
                IllegalStateException("Model not downloaded. Call download() first.")
            )
        }

        initEngineIfNeeded()

        runCatching {
            val voiceStyleJson = voiceStyleLoader.load(voice.resourcePath)
            val wav = engine.generate(
                text = text,
                lang = lang,
                voiceStyleJson = voiceStyleJson,
                speed = speed,
                steps = config.inferenceSteps,
            )
            val durationMs = wavDurationMs(wav)
            GenerateResult.Success(wav, durationMs)
        }.getOrElse { cause ->
            GenerateResult.Error(cause)
        }
    }

    /**
     * Splits [text] into sentences and generates WAV for each chunk sequentially.
     *
     * Emits [GenerateResult.Success] for each chunk as soon as it is ready —
     * so you can start playing the first chunk while the next one is still generating.
     *
     * Error in one chunk does not stop the flow — [GenerateResult.Error] is emitted
     * for that chunk and generation continues with the next one.
     *
     * Useful for long texts in a prefetch pipeline:
     * ```kotlin
     * tts.generateChunked("Long text with multiple sentences...")
     *     .collect { result ->
     *         if (result is GenerateResult.Success) {
     *             queue.enqueue(result.wav)
     *         }
     *     }
     * ```
     */
    fun generateChunked(
        text: String,
        lang: String = config.defaultLang,
        voice: SupertonicVoice = config.defaultVoice,
        speed: Float = config.defaultSpeed,
    ) : Flow<GenerateResult> = flow<GenerateResult> {
        val chunks = TextChunker().split(text)
        for (chunk in chunks)
            emit(generate(chunk, lang, voice, speed))
    }.flowOn(Dispatchers.Default)

    /**
     * Convenient method – generates and plays audio via the platform's native player.
     * Blocks until playback ends.
     *
     * If you need a prefetch or custom player, use [generate] directly.
     */
    suspend fun speak(
        text: String,
        lang: String = config.defaultLang,
        voice: SupertonicVoice = config.defaultVoice,
        speed: Float = config.defaultSpeed,
    ) {
        val result = generate(text, lang, voice, speed)
        if (result is GenerateResult.Success) {
            withContext(Dispatchers.Default) {
                PlatformPlayer.play(result.wav)
            }
        }
    }

    /** Abort the current generation or playback. */
    fun stop() {
        engine.cancel()
        PlatformPlayer.stop()
    }

    /** Release ORT sessions and stop coroutines. */
    fun close() {
        stop()
        engine.close()
        scope.cancel()
    }

    // --- Internal ---

    private fun initEngineIfNeeded() {
        if (!engine.isLoaded) {
            engine.load()
        }
    }

    private fun wavDurationMs(wav: ByteArray): Long {
        if (wav.size < 44) return 0L
        val dataSize = wav.size - 44  // minus WAV header
        val sampleRate = 44100
        val bytesPerSample = 2  // 16-bit
        return (dataSize.toLong() * 1000L) / (sampleRate * bytesPerSample)
    }
}
