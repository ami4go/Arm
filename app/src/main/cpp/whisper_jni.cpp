/**
 * JNI bridge for whisper.cpp — Speech-to-Text engine
 *
 * Provides native interface between Kotlin SttEngine and whisper.cpp C API.
 * Optimized for Arm NEON on MediaTek Dimensity 7050 (Realme Narzo 70 Pro 5G).
 *
 * Key optimizations:
 * - Uses int8 quantized model (ggml-tiny-q8_0.bin) for reduced memory
 * - NEON SIMD intrinsics via ggml-aarch64 for fast matrix operations
 * - Single-threaded context to minimize overhead on mobile
 */

#include <android/log.h>
#include <jni.h>
#include <string>


#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef HAS_WHISPER
#include "whisper.h"

struct WhisperContext {
  struct whisper_context *ctx;
};

extern "C" {

JNIEXPORT jlong JNICALL Java_com_arm_translator_SttEngine_nativeInit(
    JNIEnv *env, jobject obj, jstring modelPath) {

  const char *path = env->GetStringUTFChars(modelPath, nullptr);
  LOGI("Loading Whisper model from: %s", path);

  struct whisper_context_params cparams = whisper_context_default_params();
  cparams.use_gpu = false; // CPU only — use NEON

  struct whisper_context *ctx =
      whisper_init_from_file_with_params(path, cparams);
  env->ReleaseStringUTFChars(modelPath, path);

  if (ctx == nullptr) {
    LOGE("Failed to load Whisper model");
    return 0;
  }

  auto *wrapper = new WhisperContext();
  wrapper->ctx = ctx;

  LOGI("Whisper model loaded successfully");
  return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT jstring JNICALL Java_com_arm_translator_SttEngine_nativeTranscribe(
    JNIEnv *env, jobject obj, jlong handle, jfloatArray audioSamples,
    jstring languageCode) {

  auto *wrapper = reinterpret_cast<WhisperContext *>(handle);
  if (wrapper == nullptr || wrapper->ctx == nullptr) {
    return env->NewStringUTF("");
  }

  jfloat *samples = env->GetFloatArrayElements(audioSamples, nullptr);
  jsize numSamples = env->GetArrayLength(audioSamples);
  const char *lang = env->GetStringUTFChars(languageCode, nullptr);

  LOGI("Transcribing %d samples (%.1fs) [lang=%s]", numSamples,
       numSamples / 16000.0f, lang);

  // Configure Whisper parameters for speed
  struct whisper_full_params params =
      whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  params.n_threads = 4;         // Use 4 threads on Dimensity 7050
  params.language = lang;       // Use provided language code (or "auto")
  params.translate = false;     // Don't translate, just transcribe
  params.no_timestamps = true;  // Skip timestamps for speed
  params.single_segment = true; // Process as single segment
  params.print_progress = false;
  params.print_timestamps = false;
  params.print_special = false;

  // Run inference
  int result = whisper_full(wrapper->ctx, params, samples, numSamples);

  env->ReleaseFloatArrayElements(audioSamples, samples, 0);
  env->ReleaseStringUTFChars(languageCode, lang);

  if (result != 0) {
    LOGE("Whisper transcription failed with code %d", result);
    return env->NewStringUTF("");
  }

  // Collect transcription result
  std::string text;
  int numSegments = whisper_full_n_segments(wrapper->ctx);
  for (int i = 0; i < numSegments; i++) {
    text += whisper_full_get_segment_text(wrapper->ctx, i);
  }

  LOGI("Transcription: \"%s\"", text.c_str());
  return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL Java_com_arm_translator_SttEngine_nativeRelease(
    JNIEnv *env, jobject obj, jlong handle) {

  auto *wrapper = reinterpret_cast<WhisperContext *>(handle);
  if (wrapper != nullptr) {
    if (wrapper->ctx != nullptr) {
      whisper_free(wrapper->ctx);
    }
    delete wrapper;
  }
  LOGI("Whisper context released");
}

} // extern "C"

#else
// Stub implementation when whisper.cpp is not available

extern "C" {

JNIEXPORT jlong JNICALL Java_com_arm_translator_SttEngine_nativeInit(
    JNIEnv *env, jobject obj, jstring modelPath) {
  LOGI("Whisper stub: model loading skipped (whisper.cpp not linked)");
  return 0;
}

JNIEXPORT jstring JNICALL Java_com_arm_translator_SttEngine_nativeTranscribe(
    JNIEnv *env, jobject obj, jlong handle, jfloatArray audioSamples,
    jstring languageCode) {
  return env->NewStringUTF("[Whisper not available]");
}

JNIEXPORT void JNICALL Java_com_arm_translator_SttEngine_nativeRelease(
    JNIEnv *env, jobject obj, jlong handle) {}

} // extern "C"
#endif
