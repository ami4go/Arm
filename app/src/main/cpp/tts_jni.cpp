/**
 * JNI bridge for sherpa-onnx TTS (VITS) engine
 *
 * Uses sherpa-onnx library for Text-to-Speech with VITS (Piper) models.
 * Supports both English and Hindi voices.
 *
 * Since sherpa-onnx provides a pre-built Android AAR through Gradle,
 * this file provides a simplified JNI wrapper that delegates to the
 * sherpa-onnx API. The actual TTS calls can also be made directly
 * from Kotlin using the sherpa-onnx Android SDK.
 *
 * For the competition, we use the Kotlin-level sherpa-onnx API directly
 * in TtsEngine.kt. This file is a fallback/alternative approach.
 */

#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>


#define LOG_TAG "TtsJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * For the competition build, TTS is handled via sherpa-onnx's Kotlin API.
 * This native layer provides stub functions to satisfy JNI linking.
 *
 * The actual TTS implementation uses:
 *   com.k2fsa.sherpa.onnx.OfflineTts (Kotlin API)
 * which internally uses ONNX Runtime with NEON acceleration.
 */

extern "C" {

JNIEXPORT jlong JNICALL Java_com_arm_translator_TtsEngine_nativeInit(
    JNIEnv *env, jobject obj, jstring enModelPath, jstring hiModelPath,
    jstring espeakDataPath) {

  const char *enPath = env->GetStringUTFChars(enModelPath, nullptr);
  const char *hiPath = env->GetStringUTFChars(hiModelPath, nullptr);
  const char *espeakPath = env->GetStringUTFChars(espeakDataPath, nullptr);

  LOGI("TTS Init: EN=%s, HI=%s, eSpeak=%s", enPath, hiPath, espeakPath);

  env->ReleaseStringUTFChars(enModelPath, enPath);
  env->ReleaseStringUTFChars(hiModelPath, hiPath);
  env->ReleaseStringUTFChars(espeakDataPath, espeakPath);

  // Return non-zero handle to indicate success
  // Actual TTS is handled via sherpa-onnx Kotlin API
  return 1;
}

JNIEXPORT jfloatArray JNICALL
Java_com_arm_translator_TtsEngine_nativeSynthesize(JNIEnv *env, jobject obj,
                                                   jlong handle, jstring text,
                                                   jint voiceId) {

  const char *textStr = env->GetStringUTFChars(text, nullptr);
  LOGI("TTS Synthesize (voice=%d): \"%s\"", voiceId, textStr);
  env->ReleaseStringUTFChars(text, textStr);

  // Return empty array — actual synthesis done via Kotlin sherpa-onnx API
  // This stub exists for JNI linkage compatibility
  jfloatArray result = env->NewFloatArray(0);
  return result;
}

JNIEXPORT void JNICALL Java_com_arm_translator_TtsEngine_nativeRelease(
    JNIEnv *env, jobject obj, jlong handle) {
  LOGI("TTS Released");
}

} // extern "C"
