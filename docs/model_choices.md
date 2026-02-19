# Model Choices & Rationale

## STT: Whisper-tiny (int8)

### Why Whisper-tiny?

| Criterion | Whisper-tiny | Wav2Vec2-lite | Vosk |
|-----------|-------------|---------------|------|
| **Size** | 40MB (int8) | ~100MB | ~50MB |
| **Hindi Support** | ✅ Native | ❌ English only | ✅ Limited |
| **Accuracy** | Good for clear speech | Very good for English | Good |
| **NEON Optimization** | ✅ ggml-aarch64 | ❌ Requires PyTorch | ❌ Kaldi C++ |
| **Android Support** | ✅ Official example | ⚠️ Complex setup | ✅ SDK available |
| **Quantization** | ✅ GGML int8/int4 | ⚠️ ONNX only | ❌ Limited |

**Decision**: Whisper-tiny offers the best balance of multi-language support (Hindi + English), small footprint, and NEON-optimized inference via whisper.cpp.

### Quantization Impact
- **float16** → **int8**: 50% size reduction, <2% WER increase
- Model: 75MB → 40MB
- Inference speed: ~15% faster due to NEON int8 dotprod

---

## Translation LLM: Gemma-2B-IT (int4 Q4_K_M)

### Why Gemma-2B-IT?

| Criterion | Gemma-2B-IT | Phi-2 | TinyLlama-1.1B |
|-----------|-------------|-------|-----------------|
| **Size (int4)** | 1.5GB | 1.8GB | 700MB |
| **Hindi Translation** | ✅ Trained on Hindi | ⚠️ Limited Hindi | ❌ Poor Hindi |
| **Translation Quality** | ✅ Good | ✅ Good for English | ❌ Poor |
| **Instruction Following** | ✅ IT variant | ✅ | ⚠️ Base model |
| **llama.cpp Support** | ✅ GGUF native | ✅ GGUF | ✅ GGUF |
| **NEON Kernels** | ✅ Q4_K_M optimized | ✅ | ✅ |

**Decision**: Gemma-2B-IT is Google's instruction-tuned model with explicit Hindi training data. The IT (Instruction-Tuned) variant follows translation prompts reliably. At Q4_K_M quantization, it fits in ~1.5GB RAM while retaining translation accuracy.

### Quantization Impact
- **float16** → **int4 Q4_K_M**: 75% size reduction
- Model: 5GB → 1.5GB
- Translation quality: Negligible degradation for simple sentences
- Inference: 2-3× faster with NEON dotprod kernels

### Prompt Engineering
We use Gemma's chat template for reliable translation:
```
<start_of_turn>user
Translate the following Hindi text to English. Output ONLY the translation.
Hindi text: नमस्ते, आप कैसे हैं?
<end_of_turn>
<start_of_turn>model
English translation:
```

---

## TTS: VITS (Piper) via sherpa-onnx

### Why VITS/Piper?

| Criterion | VITS (Piper) | FastSpeech2+HiFiGAN | eSpeak |
|-----------|-------------|---------------------|--------|
| **Architecture** | End-to-end | Two-stage | Rule-based |
| **Quality** | ✅ Natural | ✅ Natural | ❌ Robotic |
| **Latency** | ~500ms | ~800ms (two passes) | ~50ms |
| **Size** | 60MB per voice | ~100MB total | ~5MB |
| **Hindi Voice** | ✅ hi_IN-swara | ⚠️ Limited | ✅ |
| **Android SDK** | ✅ sherpa-onnx AAR | ❌ Custom build | ✅ |
| **ONNX Runtime** | ✅ Native | ❌ PyTorch | N/A |

**Decision**: VITS via Piper is the most practical choice — it's end-to-end (single model), has pre-trained Hindi voices, and runs efficiently on ONNX Runtime with NEON acceleration through the sherpa-onnx Android SDK.

### Voice Quality vs Latency
- **medium** quality voices (~60MB): Good balance of naturalness and speed
- **low** quality voices (~20MB): Faster but less natural
- **high** quality voices (~100MB): Best quality but slower

We use **medium** as the default for conversational use.
