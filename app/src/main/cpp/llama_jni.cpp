/**
 * JNI bridge for llama.cpp — LLM Translation Engine
 *
 * Provides native interface between Kotlin TranslationEngine and llama.cpp C
 * API. Uses Gemma-2B-IT (Q4_K_M quantized) for Hindi<->English translation.
 *
 * Key optimizations:
 * - Q4_K_M quantization reduces model to ~1.5GB
 * - NEON dotprod intrinsics for fast int4 matrix multiplication
 * - Small context size (512) for mobile speed
 * - 4 threads for Dimensity 7050 efficiency cores
 */

#include <android/log.h>
#include <cstring>
#include <jni.h>
#include <string>
#include <vector>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#ifdef HAS_LLAMA
#include "llama.h"

struct LlamaContext {
  llama_model *model;
  llama_context *ctx;
  const llama_vocab *vocab;
  int n_ctx;
  int n_vocab;
};

extern "C" {

JNIEXPORT jlong JNICALL Java_com_arm_translator_TranslationEngine_nativeInit(
    JNIEnv *env, jobject obj, jstring modelPath, jint contextSize) {

  const char *path = env->GetStringUTFChars(modelPath, nullptr);
  LOGI("Loading LLM model from: %s (context=%d)", path, contextSize);

  // Initialize llama backend
  llama_backend_init();

  // Load model
  auto model_params = llama_model_default_params();
  model_params.n_gpu_layers = 0; // CPU only — NEON acceleration
  model_params.use_mmap = true;  // Memory-map for efficiency

  llama_model *model = llama_model_load_from_file(path, model_params);
  env->ReleaseStringUTFChars(modelPath, path);

  if (model == nullptr) {
    LOGE("Failed to load LLM model");
    return 0;
  }
  LOGI("Model loaded into memory");

  // Get vocab
  const llama_vocab *vocab = llama_model_get_vocab(model);
  if (vocab == nullptr) {
    LOGE("Failed to get vocab from model");
    llama_model_free(model);
    return 0;
  }

  int n_vocab = llama_vocab_n_tokens(vocab);
  LOGI("Vocab size: %d", n_vocab);

  // Create context with safe parameters
  auto ctx_params = llama_context_default_params();
  ctx_params.n_ctx = contextSize;
  ctx_params.n_batch = contextSize;  // Match context size for batch
  ctx_params.n_ubatch = contextSize; // Match for ubatch too
  ctx_params.n_threads = 4;          // 4 threads for Dimensity 7050
  ctx_params.n_threads_batch = 4;

  llama_context *ctx = llama_new_context_with_model(model, ctx_params);
  if (ctx == nullptr) {
    LOGE("Failed to create LLM context");
    llama_model_free(model);
    return 0;
  }
  LOGI("Context created successfully");

  auto *wrapper = new LlamaContext();
  wrapper->model = model;
  wrapper->ctx = ctx;
  wrapper->vocab = vocab;
  wrapper->n_ctx = contextSize;
  wrapper->n_vocab = n_vocab;

  LOGI("LLM initialization complete (context=%d, vocab=%d)", contextSize,
       n_vocab);
  return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT jstring JNICALL
Java_com_arm_translator_TranslationEngine_nativeGenerate(
    JNIEnv *env, jobject obj, jlong handle, jstring prompt, jint maxTokens) {

  LOGI("nativeGenerate called");

  auto *wrapper = reinterpret_cast<LlamaContext *>(handle);
  if (wrapper == nullptr || wrapper->ctx == nullptr ||
      wrapper->model == nullptr) {
    LOGE("Invalid wrapper or context in nativeGenerate");
    return env->NewStringUTF("[Error: model not loaded]");
  }

  const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
  if (promptStr == nullptr) {
    LOGE("Failed to get prompt string");
    return env->NewStringUTF("[Error: null prompt]");
  }

  int promptLen = strlen(promptStr);
  LOGI("Generating with prompt length: %d chars", promptLen);

  // Tokenize prompt using vocab
  // First call to get the number of tokens needed
  int n_tokens = llama_tokenize(wrapper->vocab, promptStr, promptLen, nullptr,
                                0, true, true);
  // n_tokens is negative if the buffer is too small (returns -needed_size)
  if (n_tokens == 0) {
    LOGE("Tokenization produced 0 tokens");
    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF("[Error: empty tokenization]");
  }

  int needed = n_tokens < 0 ? -n_tokens : n_tokens;
  LOGI("Need %d tokens for prompt", needed);

  if (needed >= wrapper->n_ctx) {
    LOGW("Prompt too long (%d tokens), truncating to %d", needed,
         wrapper->n_ctx - 64);
    needed = wrapper->n_ctx - 64; // Leave room for generation
  }

  std::vector<llama_token> tokens(needed);
  n_tokens = llama_tokenize(wrapper->vocab, promptStr, promptLen, tokens.data(),
                            tokens.size(), true, true);
  env->ReleaseStringUTFChars(prompt, promptStr);

  if (n_tokens < 0) {
    LOGE("Tokenization failed even with sized buffer");
    return env->NewStringUTF("[Error: tokenization failed]");
  }
  tokens.resize(n_tokens);
  LOGI("Prompt tokenized to %d tokens", n_tokens);

  // Clear the KV cache for a fresh start
  llama_memory_clear(llama_get_memory(wrapper->ctx), true);
  LOGI("KV cache cleared");

  // Process the prompt using llama_batch_get_one (simple API)
  LOGI("Decoding prompt...");
  llama_batch prompt_batch = llama_batch_get_one(tokens.data(), n_tokens);
  int decode_result = llama_decode(wrapper->ctx, prompt_batch);
  if (decode_result != 0) {
    LOGE("llama_decode failed for prompt, code=%d", decode_result);
    return env->NewStringUTF("[Error: decode failed]");
  }
  LOGI("Prompt decoded successfully");

  // Set up greedy sampler for fast generation
  auto sparams = llama_sampler_chain_default_params();
  sparams.no_perf = true;
  llama_sampler *smpl = llama_sampler_chain_init(sparams);
  llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
  LOGI("Sampler initialized");

  // Generate tokens one at a time
  std::string result;
  int n_generated = 0;

  // Limit generation on mobile to avoid ANR
  int actualMax = maxTokens < 128 ? maxTokens : 128;

  while (n_generated < actualMax) {
    // Sample next token
    llama_token new_token = llama_sampler_sample(smpl, wrapper->ctx, -1);

    LOGI("Token %d: id=%d", n_generated, new_token);

    // Check for end of generation
    if (llama_vocab_is_eog(wrapper->vocab, new_token)) {
      LOGI("EOG reached after %d tokens", n_generated);
      break;
    }

    // Accept the token
    llama_sampler_accept(smpl, new_token);

    // Convert token to text
    char buf[256];
    int len = llama_token_to_piece(wrapper->vocab, new_token, buf,
                                   sizeof(buf) - 1, 0, true);
    if (len > 0) {
      buf[len] = '\0';
      result.append(buf, len);
      LOGI("Token text: \"%s\" (total: \"%s\")", buf, result.c_str());
    }

    // Decode the new token for next iteration
    llama_batch next_batch = llama_batch_get_one(&new_token, 1);
    decode_result = llama_decode(wrapper->ctx, next_batch);
    if (decode_result != 0) {
      LOGE("llama_decode failed at token %d, code=%d", n_generated,
           decode_result);
      break;
    }

    n_generated++;

    // Stop on newline (translation should be a single line)
    if (result.find('\n') != std::string::npos && n_generated > 3) {
      LOGI("Newline found, stopping generation");
      break;
    }
  }

  llama_sampler_free(smpl);

  LOGI("Generated %d tokens: \"%s\"", n_generated, result.c_str());

  if (result.empty()) {
    return env->NewStringUTF("[No translation generated]");
  }

  return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL Java_com_arm_translator_TranslationEngine_nativeRelease(
    JNIEnv *env, jobject obj, jlong handle) {

  auto *wrapper = reinterpret_cast<LlamaContext *>(handle);
  if (wrapper != nullptr) {
    if (wrapper->ctx != nullptr) {
      llama_free(wrapper->ctx);
    }
    if (wrapper->model != nullptr) {
      llama_model_free(wrapper->model);
    }
    delete wrapper;
  }
  llama_backend_free();
  LOGI("LLM context released");
}

} // extern "C"

#else
// Stub implementation

extern "C" {

JNIEXPORT jlong JNICALL Java_com_arm_translator_TranslationEngine_nativeInit(
    JNIEnv *env, jobject obj, jstring modelPath, jint contextSize) {
  LOGI("Llama stub: model loading skipped");
  return 0;
}

JNIEXPORT jstring JNICALL
Java_com_arm_translator_TranslationEngine_nativeGenerate(
    JNIEnv *env, jobject obj, jlong handle, jstring prompt, jint maxTokens) {
  return env->NewStringUTF(
      "[Translation not available - llama.cpp not compiled]");
}

JNIEXPORT void JNICALL Java_com_arm_translator_TranslationEngine_nativeRelease(
    JNIEnv *env, jobject obj, jlong handle) {}

} // extern "C"
#endif
