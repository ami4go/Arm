package com.arm.translator

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Text-to-Speech engine using sherpa-onnx VITS model via JNI.
 * Uses Piper VITS models exported as ONNX, running on ONNX Runtime
 * with Arm NEON acceleration.
 *
 * Supports English and Hindi voices.
 */
class TtsEngine(context: Context, modelsDir: File) {

    companion object {
        private const val TAG = "TtsEngine"
        // Piper VITS model files
        private const val EN_MODEL_FILE = "en_US-amy-medium.onnx"
        private const val EN_CONFIG_FILE = "en_US-amy-medium.onnx.json"
        private const val HI_MODEL_FILE = "hi_IN-swara-medium.onnx"
        private const val HI_CONFIG_FILE = "hi_IN-swara-medium.onnx.json"
        private const val ESPEAK_DATA_DIR = "espeak-ng-data"
    }

    private var nativeHandle: Long = 0L
    private val modelsDirectory: File = modelsDir

    init {
        // Extract TTS model files from assets
        extractTtsModels(context, modelsDir)

        val enModelPath = File(modelsDir, EN_MODEL_FILE).absolutePath
        val hiModelPath = File(modelsDir, HI_MODEL_FILE).absolutePath
        val espeakDataPath = File(modelsDir, ESPEAK_DATA_DIR).absolutePath

        Log.i(TAG, "Initializing VITS TTS engine...")
        nativeHandle = nativeInit(enModelPath, hiModelPath, espeakDataPath)

        if (nativeHandle != 0L) {
            Log.i(TAG, "TTS engine initialized successfully")
        } else {
            Log.w(TAG, "TTS engine init failed, will use fallback mode")
        }
    }

    private fun extractTtsModels(context: Context, modelsDir: File) {
        val filesToExtract = listOf(
            EN_MODEL_FILE, EN_CONFIG_FILE,
            HI_MODEL_FILE, HI_CONFIG_FILE
        )

        for (fileName in filesToExtract) {
            val destFile = File(modelsDir, fileName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("models/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Extracted $fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract $fileName: ${e.message}")
                }
            }
        }

        // Extract espeak-ng-data directory
        val espeakDir = File(modelsDir, ESPEAK_DATA_DIR)
        if (!espeakDir.exists()) {
            espeakDir.mkdirs()
            try {
                val espeakFiles = context.assets.list("models/$ESPEAK_DATA_DIR") ?: emptyArray()
                for (f in espeakFiles) {
                    val dest = File(espeakDir, f)
                    context.assets.open("models/$ESPEAK_DATA_DIR/$f").use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Log.i(TAG, "Extracted espeak-ng-data (${espeakFiles.size} files)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract espeak-ng-data: ${e.message}")
            }
        }
    }

    /**
     * Synthesize speech from text.
     * Automatically detects language and uses appropriate voice model.
     * @return Float array of PCM audio at 22050Hz
     */
    fun synthesize(text: String): FloatArray {
        if (nativeHandle == 0L) {
            Log.w(TAG, "TTS not initialized, returning empty audio")
            return FloatArray(0)
        }

        // Detect if text is Hindi (contains Devanagari) or English
        val isHindi = text.any { it.code in 0x0900..0x097F }
        val voiceId = if (isHindi) 1 else 0  // 0 = English, 1 = Hindi

        Log.i(TAG, "Synthesizing (${if (isHindi) "Hindi" else "English"}): \"$text\"")
        val startTime = System.currentTimeMillis()

        val audioSamples = nativeSynthesize(nativeHandle, text, voiceId)

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "TTS completed in ${elapsed}ms, generated ${audioSamples.size} samples")

        return audioSamples
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    // JNI methods implemented in tts_jni.cpp
    private external fun nativeInit(
        enModelPath: String,
        hiModelPath: String,
        espeakDataPath: String
    ): Long

    private external fun nativeSynthesize(handle: Long, text: String, voiceId: Int): FloatArray
    private external fun nativeRelease(handle: Long)
}
