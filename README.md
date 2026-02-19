# Arm Translator — On-Device Speech-to-Speech Translation

> **Bharat AI-SoC Student Challenge • Problem Statement 4**  
> Real-Time On-Device Speech-to-Speech Translation using NEON on Arm CPU

## 🎯 Overview

A fully local, real-time speech-to-speech translation system (Hindi ↔ English) running on an Arm-based Android smartphone (Realme Narzo 70 Pro 5G). **Zero cloud dependency** — all inference runs on-device using Arm NEON-optimized models.

### Pipeline Architecture

```
🎤 Mic → [whisper.cpp STT] → [Gemma-2B LLM Translation] → [VITS TTS] → 🔊 Speaker
         (int8 GGML, 40MB)    (int4 GGUF, 1.5GB)          (ONNX, 60MB)
```

## 📱 Target Hardware

| Property | Value |
|----------|-------|
| **Device** | Realme Narzo 70 Pro 5G |
| **SoC** | MediaTek Dimensity 7050 |
| **CPU** | 4× Cortex-A78 + 4× Cortex-A55 |
| **Architecture** | Armv8.2-A with NEON + dot-product |
| **RAM** | 8GB |
| **Acceleration** | NEON SIMD (no SME2) |

## 🛠️ Build Instructions

### Prerequisites

- **Android Studio** (Arctic Fox or later) with:
  - Android SDK 34
  - NDK r26+ (install via SDK Manager → SDK Tools → NDK)
  - CMake 3.22.1 (install via SDK Manager → SDK Tools → CMake)
- **Git** (for cloning dependencies)
- **Python 3.8+** (for model conversion scripts)
- **~4GB free disk space** for models

### Step 1: Clone Native Dependencies

```bash
cd app/src/main/cpp
git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git
git clone --depth 1 https://github.com/ggerganov/llama.cpp.git
```

### Step 2: Download & Prepare Models

#### Whisper-tiny (STT — 40MB)
```bash
# Download the pre-quantized GGML model
cd app/src/main/assets/models
curl -L -o ggml-tiny.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
```

#### Gemma-2B-IT (Translation — 1.5GB)
```bash
# Option A: Download pre-quantized GGUF from HuggingFace
# (Search "gemma-2b-it GGUF" on HuggingFace for community uploads)
# Place the .gguf file in your phone's storage at:
# /storage/emulated/0/Android/data/com.arm.translator/files/models/gemma-2b-it-q4_k_m.gguf

# Option B: Convert yourself
pip install huggingface-hub
huggingface-cli download google/gemma-2b-it --local-dir ./gemma-2b-it
cd ../../cpp/llama.cpp
python convert_hf_to_gguf.py ../../../../models/gemma-2b-it --outtype f16
./llama-quantize gemma-2b-it-f16.gguf gemma-2b-it-q4_k_m.gguf Q4_K_M
```

#### VITS TTS Models (60MB each)
```bash
# English voice
cd app/src/main/assets/models
curl -L -o en_US-amy-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx
curl -L -o en_US-amy-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json

# Hindi voice
curl -L -o hi_IN-swara-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/swara/medium/hi_IN-swara-medium.onnx
curl -L -o hi_IN-swara-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/swara/medium/hi_IN-swara-medium.onnx.json
```

### Step 3: Build & Run

1. Open the project folder in **Android Studio**
2. Wait for Gradle sync to complete
3. Connect your Realme Narzo 70 Pro 5G via USB (enable USB debugging)
4. Click **Run** (▶️) or `Shift+F10`
5. Grant microphone permission when prompted

### Step 4: Push Large Model to Device

Since Gemma-2B (1.5GB) is too large for APK assets:
```bash
adb push gemma-2b-it-q4_k_m.gguf /storage/emulated/0/Android/data/com.arm.translator/files/models/
```

## 🏗️ Architecture

### Component Stack

| Component | Library | Model | Size | Quantization |
|-----------|---------|-------|------|-------------|
| **Audio Capture** | Android AudioRecord | — | — | — |
| **STT** | whisper.cpp | Whisper-tiny | 40MB | int8 GGML |
| **Translation** | llama.cpp | Gemma-2B-IT | 1.5GB | int4 Q4_K_M |
| **TTS** | sherpa-onnx | VITS (Piper) | 60MB | float16 ONNX |
| **Audio Playback** | Android AudioTrack | — | — | — |

### Data Flow

```
┌──────────────┐     16kHz PCM      ┌──────────────┐
│  Microphone  │ ──────────────────► │  whisper.cpp  │
│  (AudioRecord)│                    │  (STT, int8)  │
└──────────────┘                    └──────┬───────┘
                                           │ Text (Hindi/English)
                                           ▼
                                    ┌──────────────┐
                                    │  llama.cpp    │
                                    │  (Gemma-2B    │
                                    │   Q4_K_M)     │
                                    └──────┬───────┘
                                           │ Translated Text
                                           ▼
                                    ┌──────────────┐     22kHz PCM
                                    │  sherpa-onnx  │ ─────────────►  🔊
                                    │  (VITS TTS)   │              Speaker
                                    └──────────────┘
```

### Arm Optimization Details

#### NEON Intrinsics (Armv8.2-A)
- **All three engines** are compiled with `-march=armv8.2-a+dotprod+fp16`
- whisper.cpp uses NEON-optimized `ggml-aarch64.c` for matrix operations
- llama.cpp uses NEON dotprod for int4 matrix multiplication via Q4_K_M kernels
- ONNX Runtime (via sherpa-onnx) uses NEON for VITS inference

#### Quantization Strategy
- **Whisper**: int8 reduces model 4× while maintaining WER (Word Error Rate) close to float16
- **Gemma-2B**: int4 (Q4_K_M) reduces model 4× with <1% accuracy degradation on translation tasks
- **VITS**: float16 ONNX, as further quantization degrades audio quality

#### Threading
- STT: 4 threads (Cortex-A78 cores)
- LLM: 4 threads with batch size 32
- TTS: Uses ONNX Runtime's internal threading

## 📊 Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| STT Latency | <2s for 5s audio | Whisper-tiny, int8, 4 threads |
| Translation Latency | <3s for single sentence | Gemma-2B, int4, greedy sampling |
| TTS Latency | <1s for single sentence | VITS, float16 |
| End-to-End | <6s total | Suitable for conversational use |
| Memory | <3GB peak | Within 8GB device RAM |
| Thermal | No throttling in 5min use | Efficient quantized models |

## 📂 Project Structure

```
Arm_Project/
├── app/
│   ├── src/main/
│   │   ├── java/com/arm/translator/
│   │   │   ├── MainActivity.kt          # UI + pipeline orchestration
│   │   │   ├── AudioCaptureManager.kt   # Microphone recording (16kHz)
│   │   │   ├── AudioPlaybackManager.kt  # Speaker output (22kHz)
│   │   │   ├── SttEngine.kt             # whisper.cpp JNI wrapper
│   │   │   ├── TranslationEngine.kt     # llama.cpp JNI wrapper
│   │   │   ├── TtsEngine.kt             # sherpa-onnx VITS wrapper
│   │   │   └── PipelineOrchestrator.kt  # Stage coordination
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt            # NEON build config
│   │   │   ├── whisper_jni.cpp           # Whisper native bridge
│   │   │   ├── llama_jni.cpp             # Gemma native bridge
│   │   │   ├── tts_jni.cpp               # TTS native bridge
│   │   │   ├── whisper.cpp/              # (git submodule)
│   │   │   └── llama.cpp/               # (git submodule)
│   │   ├── res/                          # UI resources
│   │   └── assets/models/               # Model files
│   └── build.gradle.kts
├── docs/
│   ├── architecture.md
│   ├── model_choices.md
│   ├── optimizations.md
│   └── performance_report.md
└── README.md
```

## 👤 Team

- Solo developer — Bharat AI-SoC Student Challenge 2026

## 📜 License

This project is built for the Arm Bharat AI-SoC Student Challenge using open-source components:
- whisper.cpp — MIT License
- llama.cpp — MIT License
- sherpa-onnx — Apache 2.0 License
- Piper Voices — MIT License
