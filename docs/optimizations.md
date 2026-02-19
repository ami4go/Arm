# Arm Optimizations — NEON and Quantization

## Target Hardware

**Realme Narzo 70 Pro 5G**
- SoC: MediaTek Dimensity 7050
- CPU: 4× Cortex-A78 (2.5GHz) + 4× Cortex-A55 (2.0GHz)
- Architecture: Armv8.2-A
- SIMD: NEON with dotprod extension
- SME2: ❌ Not available on this SoC

## NEON Optimization Strategy

### What is NEON?
NEON is Arm's Advanced SIMD (Single Instruction, Multiple Data) extension that enables parallel processing of multiple data elements in a single instruction. On Armv8.2-A, NEON provides:
- 128-bit vector registers (32 registers)
- Integer and floating-point SIMD operations
- Dot product instructions (`sdot`, `udot`) for int8 matrix multiplication
- Half-precision (fp16) support for efficient inference

### How We Leverage NEON

#### Compiler Flags
```cmake
-march=armv8.2-a+dotprod+fp16  # Enable all available extensions
-O3                              # Maximum optimization level
-ffast-math                      # Allow float optimizations
```

#### whisper.cpp NEON Usage
- `ggml-aarch64.c` contains hand-optimized NEON kernels for:
  - `ggml_vec_dot_q8_0_q8_0`: int8 dot product using `sdot` instruction
  - Matrix multiplication using 4×4 block tiling
  - Quantization/dequantization with NEON pack/unpack
- Auto-detected at compile time via `__ARM_NEON` preprocessor flag

#### llama.cpp NEON Usage
- Q4_K_M quantization format is specifically designed for NEON efficiency:
  - 4-bit weights are packed into NEON vectors
  - `sdot` instruction computes 4 int8 dot products per cycle
  - Block size of 256 optimized for L1/L2 cache on Cortex-A78
- KleidiAI micro-kernels (when available) provide additional optimization

#### ONNX Runtime NEON Usage
- XNNPACK backend uses NEON for:
  - Convolution operations in VITS encoder
  - Matrix multiplication in attention layers
  - Activation functions (GELU, Sigmoid) with NEON vectorization

## Quantization Details

### int8 Quantization (Whisper)
```
Original: float16 weights → 2 bytes per weight
Quantized: int8 weights + float16 scale → ~1 byte per weight
Savings: 50% memory, 15% faster inference
Accuracy impact: <2% WER increase
```

### int4 Quantization Q4_K_M (Gemma-2B)
```
Original: float16 weights → 2 bytes per weight
Quantized: 4-bit weights + block scales → ~0.5 bytes per weight
Savings: 75% memory, 2-3× faster inference
Accuracy impact: <1% on translation benchmarks

Q4_K_M specifics:
- Block size: 256 weights per block
- Uses fp16 scales and min values per block
- Higher quality than Q4_0 due to k-quant optimization
- Excellent match for NEON dotprod instructions
```

### float16 (VITS TTS)
```
TTS kept at float16 because:
- Audio quality degrades significantly with int8 quantization
- VITS model is already small (~60MB)
- NEON fp16 instructions provide sufficient speed
```

## Threading Strategy

```
CPU Core Assignment (Dimensity 7050):
┌──────────────────────────────────────┐
│  Cortex-A78 cores (Performance)      │
│  Core 0: whisper/llama thread 1      │
│  Core 1: whisper/llama thread 2      │
│  Core 2: whisper/llama thread 3      │
│  Core 3: whisper/llama thread 4      │
├──────────────────────────────────────┤
│  Cortex-A55 cores (Efficiency)       │
│  Core 4: Audio capture thread        │
│  Core 5: UI thread                   │
│  Core 6-7: OS / background           │
└──────────────────────────────────────┘
```

- Inference uses 4 threads on A78 performance cores
- Audio capture runs on efficiency cores
- Sequential pipeline execution prevents core contention

## Energy Efficiency Considerations

1. **Quantized models** reduce compute by 2-4× → lower power draw
2. **Memory-mapped files** avoid loading entire model into RAM
3. **Sequential pipeline** prevents all cores from being active simultaneously
4. **Greedy sampling** for LLM avoids expensive beam search
5. **512-token context** limits LLM computation per query

## Comparison: NEON vs SME2

| Feature | NEON (our device) | SME2 (future devices) |
|---------|-------------------|----------------------|
| Vector width | 128-bit fixed | Scalable (128-2048 bit) |
| Matrix ops | Dot product only | Native outer product (MOPA) |
| Speedup | Baseline | 2-6× faster for matmul |
| Devices | All Arm64 phones | OPPO Find X9, vivo X300+ |
| KleidiAI | ✅ Supported | ✅ Auto-dispatched |

Our solution is **forward-compatible**: when deployed on an SME2 device, llama.cpp and ONNX Runtime will automatically use KleidiAI SME2 kernels without code changes.
