package com.arm.translator

import android.content.Context
import android.util.Log
import java.io.File

/**
 * LLM-based Translation engine using llama.cpp via JNI.
 * Uses Gemma-2B-IT quantized to int4 (Q4_K_M) for on-device translation.
 * Supports Hindi <-> English translation.
 * Optimized with NEON intrinsics on Arm (Dimensity 7050).
 */
class TranslationEngine(context: Context, modelsDir: File) {

    companion object {
        private const val TAG = "TranslationEngine"
        private const val MODEL_FILE = "gemma-2b-it-q4_k_m.gguf"
        private const val MAX_TOKENS = 256
        private const val CONTEXT_SIZE = 512
    }

    private var nativeHandle: Long = 0L

    init {
        // Try to find the model in multiple locations
        val internalModel = File(modelsDir, MODEL_FILE)
        val externalModel = File(context.getExternalFilesDir(null), "models/$MODEL_FILE")

        val modelFile = when {
            internalModel.exists() -> {
                Log.i(TAG, "Found model in internal storage")
                internalModel
            }
            externalModel.exists() -> {
                Log.i(TAG, "Found model in external storage: ${externalModel.absolutePath}")
                externalModel
            }
            else -> {
                Log.w(TAG, "Model not found in either location:")
                Log.w(TAG, "  Internal: ${internalModel.absolutePath}")
                Log.w(TAG, "  External: ${externalModel.absolutePath}")
                Log.w(TAG, "Please copy $MODEL_FILE to ${externalModel.absolutePath}")
                null
            }
        }

        if (modelFile != null) {
            Log.i(TAG, "Loading Gemma model (${modelFile.length() / 1024 / 1024}MB)...")
            nativeHandle = nativeInit(modelFile.absolutePath, CONTEXT_SIZE)
            if (nativeHandle != 0L) {
                Log.i(TAG, "Gemma model loaded successfully")
            } else {
                Log.e(TAG, "Failed to load Gemma model (nativeInit returned 0)")
            }
        } else {
            Log.w(TAG, "Model file not found, translation will use fallback mode")
        }
    }

    /**
     * Translate text from one language to another using the on-device LLM.
     * Uses a carefully crafted prompt to get clean translations.
     */
    fun translate(text: String, fromLanguage: String, toLanguage: String): String {
        if (nativeHandle == 0L) {
            Log.w(TAG, "Gemma not initialized, returning placeholder")
            return "[LLM not loaded - place $MODEL_FILE in phone storage/Android/data/com.arm.translator/files/models/]"
        }

        // Construct translation prompt
        val prompt = buildTranslationPrompt(text, fromLanguage, toLanguage)

        Log.i(TAG, "Translating: \"$text\" ($fromLanguage -> $toLanguage)")
        val startTime = System.currentTimeMillis()

        val rawResult = nativeGenerate(nativeHandle, prompt, MAX_TOKENS)

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Translation completed in ${elapsed}ms")

        // Clean up the result — extract just the translation
        return cleanTranslationOutput(rawResult, toLanguage)
    }

    private fun buildTranslationPrompt(text: String, fromLang: String, toLang: String): String {
        return """<start_of_turn>user
Translate the following $fromLang text to $toLang. Output ONLY the translation, nothing else. Do not add explanations.

$fromLang text: $text
<end_of_turn>
<start_of_turn>model
$toLang translation: """
    }

    private fun cleanTranslationOutput(rawOutput: String, targetLang: String): String {
        // Remove any model artifacts and extract clean translation
        var cleaned = rawOutput
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("model", "")
            .replace("$targetLang translation:", "")
            .trim()

        // Take only the first line/sentence to avoid rambling
        val firstNewline = cleaned.indexOf('\n')
        if (firstNewline > 0) {
            cleaned = cleaned.substring(0, firstNewline).trim()
        }

        return cleaned
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    // JNI methods implemented in llama_jni.cpp
    private external fun nativeInit(modelPath: String, contextSize: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeRelease(handle: Long)
}
