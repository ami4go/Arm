package com.arm.translator

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Speech-to-Text engine using whisper.cpp via JNI.
 * Uses Whisper-tiny model quantized to int8 for efficient on-device inference.
 * Optimized for Arm NEON on Realme Narzo 70 Pro 5G (Dimensity 7050).
 */
class SttEngine(context: Context, modelsDir: File) {

    companion object {
        private const val TAG = "SttEngine"
        private const val MODEL_FILE = "ggml-tiny.bin"
    }

    private var nativeHandle: Long = 0L

    init {
        val modelFile = File(modelsDir, MODEL_FILE)

        // Copy model from assets if not already extracted
        if (!modelFile.exists()) {
            Log.i(TAG, "Extracting Whisper model to ${modelFile.absolutePath}")
            try {
                context.assets.open("models/$MODEL_FILE").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract model, using dummy mode", e)
            }
        }

        if (modelFile.exists()) {
            Log.i(TAG, "Loading Whisper model (${modelFile.length() / 1024 / 1024}MB)...")
            nativeHandle = nativeInit(modelFile.absolutePath)
            Log.i(TAG, "Whisper model loaded successfully")
        } else {
            Log.w(TAG, "Model file not found, STT will use fallback mode")
        }
    }

    /**
     * Transcribe audio samples to text.
     * @param audioSamples Float array of PCM audio at 16kHz mono, normalized to [-1, 1]
     * @return Transcribed text
     */
    fun transcribe(audioSamples: FloatArray, languageCode: String = "auto"): String {
        if (nativeHandle == 0L) {
            Log.w(TAG, "Whisper not initialized, returning placeholder")
            return "[STT not loaded - place ggml-tiny.bin in assets/models/]"
        }

        Log.i(TAG, "Transcribing ${audioSamples.size} samples (lang=$languageCode)...")
        val startTime = System.currentTimeMillis()

        val result = nativeTranscribe(nativeHandle, audioSamples, languageCode)

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Transcription completed in ${elapsed}ms: \"$result\"")

        return result.trim()
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    // JNI methods implemented in whisper_jni.cpp
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, audioSamples: FloatArray, languageCode: String): String
    private external fun nativeRelease(handle: Long)
}
