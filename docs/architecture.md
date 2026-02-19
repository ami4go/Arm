# System Architecture — Arm Translator

## Overview

The Arm Translator is a three-stage on-device speech-to-speech translation pipeline running entirely on an Arm Cortex-A78/A55 CPU (MediaTek Dimensity 7050). All inference is local — no cloud APIs are used.

## Pipeline Stages

### Stage 1: Speech-to-Text (STT)
- **Library**: whisper.cpp (C++ port of OpenAI Whisper)
- **Model**: Whisper-tiny (39M parameters)
- **Quantization**: int8 GGML (~40MB on disk)
- **Input**: 16kHz mono PCM audio (float32 normalized)
- **Output**: Transcribed text (auto-detects Hindi/English)
- **Arm Optimization**: NEON SIMD via `ggml-aarch64.c`, 4 threads on A78 cores

### Stage 2: LLM Translation
- **Library**: llama.cpp (C++ inference engine)
- **Model**: Gemma-2B-IT (Google, 2B parameters)
- **Quantization**: int4 Q4_K_M GGUF (~1.5GB on disk)
- **Input**: Source language text + translation prompt
- **Output**: Translated text in target language
- **Arm Optimization**: NEON dotprod for int4 matmul, greedy sampling, 512-token context

### Stage 3: Text-to-Speech (TTS)
- **Library**: sherpa-onnx (VITS via ONNX Runtime)
- **Model**: Piper VITS (en_US-amy-medium / hi_IN-swara-medium)
- **Format**: float16 ONNX (~60MB per voice)
- **Input**: Text string
- **Output**: 22050Hz mono PCM audio (float32)
- **Arm Optimization**: ONNX Runtime NEON backend

## Threading Model

```
Main Thread (UI)
    │
    ├── Coroutine (Dispatchers.IO)
    │   ├── AudioRecord thread (continuous PCM capture)
    │   ├── STT inference (4 NEON threads)
    │   ├── LLM inference (4 NEON threads)
    │   └── TTS inference (ONNX Runtime internal threads)
    │
    └── AudioTrack (playback on completion)
```

- Stages execute **sequentially** to minimize peak memory usage
- Each engine uses 4 threads mapped to Cortex-A78 performance cores
- Audio capture runs on a dedicated thread for continuous recording

## Memory Budget

| Component | RAM Usage | Notes |
|-----------|-----------|-------|
| Whisper-tiny (int8) | ~80MB | GGML memory-mapped |
| Gemma-2B (int4) | ~1.5GB | GGUF memory-mapped |
| VITS (float16) | ~120MB | ONNX Runtime |
| Audio buffers | ~10MB | 16kHz/22kHz PCM |
| Android framework | ~500MB | System overhead |
| **Total peak** | **~2.2GB** | Within 8GB device RAM |

## JNI Architecture

```
Kotlin Layer              JNI Bridge              Native C++ Layer
──────────────           ──────────────           ──────────────
SttEngine.kt      ←→    whisper_jni.cpp    ←→    whisper.cpp
TranslationEngine.kt ←→ llama_jni.cpp     ←→    llama.cpp
TtsEngine.kt      ←→    tts_jni.cpp       ←→    sherpa-onnx
AudioCaptureManager.kt   (Android API)
AudioPlaybackManager.kt  (Android API)
```
