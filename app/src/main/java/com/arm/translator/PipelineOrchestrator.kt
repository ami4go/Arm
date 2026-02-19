package com.arm.translator

import android.util.Log

/**
 * Orchestrates the complete speech-to-speech translation pipeline:
 * Audio Capture → STT → LLM Translation → TTS → Audio Playback
 *
 * Each stage runs sequentially but the pipeline is designed to be
 * called from a background coroutine to keep the UI responsive.
 */
class PipelineOrchestrator(
    private val sttEngine: SttEngine,
    private val translationEngine: TranslationEngine,
    private val ttsEngine: TtsEngine,
    private val audioCaptureManager: AudioCaptureManager,
    private val audioPlaybackManager: AudioPlaybackManager
) {
    companion object {
        private const val TAG = "PipelineOrchestrator"
    }

    data class PipelineResult(
        val sourceText: String,
        val translatedText: String,
        val audioOutput: FloatArray,
        val sttLatencyMs: Long,
        val translationLatencyMs: Long,
        val ttsLatencyMs: Long,
        val totalLatencyMs: Long
    )

    /**
     * Run the complete pipeline on pre-recorded audio.
     */
    fun processAudio(
        audioSamples: FloatArray,
        sourceLanguage: String,
        targetLanguage: String
    ): PipelineResult {
        val totalStart = System.currentTimeMillis()

        // Stage 1: Speech-to-Text
        Log.i(TAG, "Stage 1: STT ($sourceLanguage)")
        val sttStart = System.currentTimeMillis()
        
        // Map UI language name to ISO code for Whisper
        val sttLangCode = when (sourceLanguage.lowercase()) {
            "english" -> "en"
            "hindi" -> "hi"
            else -> "auto"
        }
        
        val transcribedText = sttEngine.transcribe(audioSamples, sttLangCode)
        val sttLatency = System.currentTimeMillis() - sttStart
        Log.i(TAG, "STT result: \"$transcribedText\" (${sttLatency}ms)")

        // Stage 2: Translation
        Log.i(TAG, "Stage 2: Translation ($sourceLanguage -> $targetLanguage)")
        val translateStart = System.currentTimeMillis()
        val translatedText = translationEngine.translate(
            transcribedText, sourceLanguage, targetLanguage
        )
        val translateLatency = System.currentTimeMillis() - translateStart
        Log.i(TAG, "Translation result: \"$translatedText\" (${translateLatency}ms)")

        // Stage 3: Text-to-Speech
        Log.i(TAG, "Stage 3: TTS")
        val ttsStart = System.currentTimeMillis()
        val audioOutput = ttsEngine.synthesize(translatedText)
        val ttsLatency = System.currentTimeMillis() - ttsStart
        Log.i(TAG, "TTS generated ${audioOutput.size} samples (${ttsLatency}ms)")

        val totalLatency = System.currentTimeMillis() - totalStart
        Log.i(TAG, "Pipeline complete: Total=${totalLatency}ms (STT=${sttLatency}ms, Translate=${translateLatency}ms, TTS=${ttsLatency}ms)")

        return PipelineResult(
            sourceText = transcribedText,
            translatedText = translatedText,
            audioOutput = audioOutput,
            sttLatencyMs = sttLatency,
            translationLatencyMs = translateLatency,
            ttsLatencyMs = ttsLatency,
            totalLatencyMs = totalLatency
        )
    }
}
