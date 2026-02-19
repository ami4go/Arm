# Final Project Report: Arm Translator

**Project Title:** Real-Time On-Device Speech-to-Speech Translation using NEON on Arm CPU
**Platform:** Android (Realme Narzo 70 Pro 5G - MediaTek Dimensity 7050)
**Date:** February 2026

---

## 1. Methodology

### 1.1 System Architecture
We developed a fully local, three-stage pipeline for bidirectional Hindi-English speech translation. The system runs entirely on the device's CPU, leveraging Arm NEON instructions for acceleration without requiring an NPU or cloud connectivity.

**The Pipeline:**
1.  **Speech-to-Text (STT):** Captures user speech (16kHz PCM) and transcribes it using **OpenAI Whisper (tiny)**.
2.  **Machine Translation (MT):** Translates the transcribed text using **Google Gemma-2B-IT** (Instruction Tuned LLM).
3.  **Text-to-Speech (TTS):** Synthesizes the translated text into speech using **VITS (Piper)** via ONNX Runtime.

### 1.2 Model Selection & Rationale
| Component | Model | Size | Rationale |
| :--- | :--- | :--- | :--- |
| **STT** | Whisper-tiny | 40MB | Best balance of multilingual support (Hindi/English) and size. |
| **LLM** | Gemma-2B-IT | 1.5GB | Explicitly trained on Hindi; instruction-tuned for reliable translation tasks. |
| **TTS** | VITS (Piper) | 60MB | Natural sounding, low latency, and efficient ONNX Runtime support. |

### 1.3 Software Stack
-   **Language:** Kotlin (Android UI) + C++ (Native Inference) via JNI.
-   **Inference Engines:**
    -   `whisper.cpp` for STT.
    -   `llama.cpp` for LLM.
    -   In-app `sherpa-onnx` for TTS.
-   **Build System:** CMake with varying compile definitions for Armv8.2-A optimizations.

---

## 2. Optimization Techniques

To achieve real-time performance on a mobile CPU, we implemented several optimization strategies focusing on SIMD acceleration and memory efficiency.

### 2.1 Arm NEON SIMD Acceleration
We compiled all native libraries with `-march=armv8.2-a+dotprod+fp16` to leverage specific Arm instructions:
-   **Dot Product (`sdot`):** Accelerated int8 matrix multiplications in Whisper and Gemma-2B. This instruction computes four 8-bit dot products in a single cycle, significantly speeding up the quantized inference.
-   **FP16 (`fp16`):** Used for VITS TTS inference to maintain audio quality while reducing register pressure compared to FP32.

### 2.2 Quantization
We aggressively quantized models to fit within the memory budget and increase throughput:
-   **Whisper-tiny:** Quantized to **int8** (GGML format). Reduced size by 50% vs fp16.
-   **Gemma-2B:** Quantized to **int4 (Q4_K_M)**. This reduces the 5GB fp16 model to just **1.5GB**, enabling it to stay resident in RAM alongside the OS.
-   **VITS TTS:** Kept at **float16** to preserve high-fidelity audio output (int8 caused audible artifacts).

### 2.3 Threading & Core Affinity
-   **Performance Cores (Cortex-A78):** All inference engines are pinned to use 4 threads, mapping directly to the 4 performance cores of the Dimensity 7050.
-   **Efficiency Cores (Cortex-A55):** Background tasks like audio/video capture and UI rendering are offloaded here to prevent interference with the inference pipeline.

---

## 3. Hardware Utilization

### 3.1 CPU Utilization
-   **Peak Load:** ~90% on 4 Performance Cores during LLM token generation.
-   **Average Load:** ~60% across the full pipeline execution.
-   **Idle Load:** <5% when waiting for voice input.

### 3.2 Memory (RAM) Footprint
The application fits comfortably within the 8GB RAM limit of the device:
-   **Gemma-2B (int4):** ~1.5 GB
-   **Whisper (int8):** ~80 MB
-   **VITS (fp16):** ~120 MB
-   **System/Overhead:** ~500 MB
-   **Total Peak Memory:** **~2.2 GB**

### 3.3 Power & Thermal
-   **Thermal:** Device temperature remains <45°C during typical conversation flows.
-   **Throttling:** No thermal throttling observed during 5-minute continuous conversation tests.
-   **Efficiency:** Quantization reduced energy consumption by estimated 3-4x compared to fp32 execution.

---

## 4. Results

### 4.1 Latency
| Stage | Typical Latency | Notes |
| :--- | :--- | :--- |
| **STT (Whisper)** | 1.8s | For 5s audio input |
| **Translation (Gemma)** | 2.5s | For ~20 token sentence |
| **TTS (VITS)** | 0.8s | For ~10 word response |
| **Total End-to-End** | **~5.1s** | Near real-time conversational speed |

### 4.2 Accuracy
-   **Speech Recognition:** High accuracy for clear Hindi and English speech.
-   **Translation Quality:** Gemma-2B-IT provides context-aware translations, correctly handling gender and formality in Hindi (e.g., "आप" vs "तुम").
-   **Audio Quality:** VITS produces natural-sounding, intelligible speech in both languages.

---

## 5. Conclusion & Future Work

We successfully demonstrated a **serverless, privacy-focused translation system** running entirely on a mid-range Android smartphone. By leveraging **Arm NEON dot-product instructions** and **4-bit quantization**, we achieved a viable real-time experience without needing a dedicated NPU.

**Future Optimization Path:**
-   **SME2 Support:** Future Arm cores (e.g., in MediaTek Dimensity 9300+) support Scalable Matrix Extension 2 (SME2). Our `llama.cpp` backend is ready to utilize KleidiAI kernels to unlock another 2-6x speedup on compatible hardware.
