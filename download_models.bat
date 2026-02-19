@echo off
REM ============================================
REM Model Download Script for Arm Translator
REM Run this from the project root directory
REM ============================================
echo.
echo ========================================
echo  Arm Translator - Model Download Script
echo ========================================
echo.

REM Create directories
if not exist "app\src\main\assets\models" mkdir "app\src\main\assets\models"
if not exist "models" mkdir "models"

echo.
echo [1/4] Downloading Whisper-tiny model (40MB)...
echo.
curl -L -o "app\src\main\assets\models\ggml-tiny.bin" "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
if %ERRORLEVEL% EQU 0 (
    echo [OK] Whisper model downloaded successfully
) else (
    echo [ERROR] Failed to download Whisper model
    echo         Please download manually from:
    echo         https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
)

echo.
echo [2/4] Downloading English TTS voice (60MB)...
echo.
curl -L -o "app\src\main\assets\models\en_US-amy-medium.onnx" "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/amy/medium/en_US-amy-medium.onnx"
curl -L -o "app\src\main\assets\models\en_US-amy-medium.onnx.json" "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/amy/medium/en_US-amy-medium.onnx.json"
echo [OK] English voice downloaded

echo.
echo [3/4] Downloading Hindi TTS voice (60MB)...
echo.
curl -L -o "app\src\main\assets\models\hi_IN-swara-medium.onnx" "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/hi/hi_IN/swara/medium/hi_IN-swara-medium.onnx"
curl -L -o "app\src\main\assets\models\hi_IN-swara-medium.onnx.json" "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/hi/hi_IN/swara/medium/hi_IN-swara-medium.onnx.json"
echo [OK] Hindi voice downloaded

echo.
echo [4/4] Gemma-2B-IT Translation Model (1.5GB)
echo.
echo NOTE: The Gemma model is too large for APK assets.
echo You need to:
echo   1. Download a pre-quantized GGUF from HuggingFace:
echo      https://huggingface.co/models?search=gemma-2b-it+gguf
echo   2. Look for a Q4_K_M variant (~1.5GB)
echo   3. Push it to your phone via ADB:
echo      adb push gemma-2b-it-q4_k_m.gguf /storage/emulated/0/Android/data/com.arm.translator/files/models/
echo.

echo.
echo ========================================
echo  Download Complete!
echo ========================================
echo.
echo Next steps:
echo  1. Open this project in Android Studio
echo  2. Clone whisper.cpp and llama.cpp:
echo     cd app\src\main\cpp
echo     git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git
echo     git clone --depth 1 https://github.com/ggerganov/llama.cpp.git
echo  3. Build and run the app
echo.
pause
