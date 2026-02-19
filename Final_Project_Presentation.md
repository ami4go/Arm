---
marp: true
theme: default
paginate: true
backgroundColor: #ffffff
header: 'Arm Translator: Real-Time On-Device Speech-to-Speech'
footer: 'Bharat AI-SoC Student Challenge 2026'
---

# **Real-Time On-Device Speech-to-Speech Translation**
## Using NEON on Arm CPU

**Platform:** Android (Realme Narzo 70 Pro 5G)
**SoC:** MediaTek Dimensity 7050 (Arm Cortex-A78)

---

# **1. Problem Statement**

**Goal:** Create a privacy-focused, serverless translation system for Hindi ↔ English.

**Constraints:**
-   **No Cloud:** All processing must be local.
-   **Low Latency:** Real-time conversational speed.
-   **Hardware:** Mid-range Smartphone (No dedicated NPU usage).
-   **Memory:** Fit within limited RAM budget.

---

# **2. Methodology: The Pipeline**

We implemented a 3-stage sequential pipeline optimized for Arm CPUs:

1.  **Speech-to-Text (STT)**
    -   *Engine:* **whisper.cpp** (OpenAI Whisper-tiny)
    -   *Input:* 16kHz PCM Audio
2.  **Machine Translation (MT)**
    -   *Engine:* **llama.cpp** (Google Gemma-2B-IT)
    -   *Optimization:* Instruction-tuned for context awareness
3.  **Text-to-Speech (TTS)**
    -   *Engine:* **sherpa-onnx** (Piper VITS)
    -   *Output:* 22kHz Natural Speech

---

# **3. Model Selection & Rationale**

| Component | Model Selected | Reasons |
| :--- | :--- | :--- |
| **STT** | **Whisper-tiny** (40MB) | excellent Hindi/English support; highly compressible. |
| **LLM** | **Gemma-2B-IT** (1.5GB) | Validated Hindi training data; "Instruction Tuned" variant follows prompts reliably. |
| **TTS** | **VITS** (60MB) | Best trade-off between naturalness and latency; ONNX Runtime support. |

---

# **4. Optimization Techniques (1/2)**

## **Arm NEON SIMD Acceleration**

Built all native engines with `-march=armv8.2-a+dotprod+fp16`:

-   **Dot Product (`sdot`):** 
    -   Computes **4x int8** operations per cycle.
    -   Accelerates Matrix Multiplication in Whisper and Gemma.
-   **FP16 (`fp16`):**
    -   Used in VITS TTS for faster floating-point math without quality loss.

---

# **4. Optimization Techniques (2/2)**

## **Quantization Strategy**

Aggressive quantization played a key role in fitting models into RAM:

-   **Whisper:** `int8` (GGML) → 50% size reduction.
-   **Gemma-2B:** `int4` (Q4_K_M) → **75% size reduction** (5GB → 1.5GB).
    -   Crucial for keeping the OS responsive.
-   **VITS:** `float16` → Kept at half-precision for audio fidelity.

---

# **5. Hardware Utilization**

## **CPU & Threading**
-   **Performance Cores (Cortex-A78):** 4 threads pinned for heavy inference (STT/LLM).
-   **Efficiency Cores (Cortex-A55):** Handles Audio I/O and UI to prevent stutter.

## **Memory Footprint**
| Component | RAM Usage |
| :--- | :--- |
| LLM (Gemma) | 1.5 GB |
| STT + TTS | 200 MB |
| Overhead | 500 MB |
| **TOTAL** | **~2.2 GB** (Safe for 8GB Device) |

---

# **6. Results**

## **Performance Metrics**

| Metric | Measured Value | Target Met? |
| :--- | :--- | :--- |
| **STT Latency** | ~1.8 sec | ✅ Yes |
| **Translation** | ~2.5 sec | ✅ Yes |
| **TTS Latency** | ~0.8 sec | ✅ Yes |
| **Total** | **~5.1 sec** | ✅ **Near Real-Time** |

## **Accuracy**
-   Reliable translation of formal vs. informal Hindi.
-   High intelligibility in noisy environments.

---

# **7. Conclusion**

-   **Feasibility Proven:** High-quality translation is possible on mid-range Android CPUs without NPU hardware.
-   **Key Enabler:** **Arm NEON** instructions + **4-bit Quantization**.
-   **Future Work:** 
    -   Integration with **SME2** (Scalable Matrix Extension) on future datasets.
    -   Streaming pipeline for even lower latency.

---

# **Thank You**

**Repository:** https://github.com/ami4go/Arm
