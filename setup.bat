@echo off
REM ============================================
REM Setup Script - Clone Native Dependencies
REM Run this from the project root directory
REM ============================================
echo.
echo ========================================
echo  Arm Translator - Setup Script
echo ========================================
echo.

echo [1/2] Cloning whisper.cpp...
if exist "app\src\main\cpp\whisper.cpp" (
    echo [SKIP] whisper.cpp already exists
) else (
    git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git "app\src\main\cpp\whisper.cpp"
    echo [OK] whisper.cpp cloned
)

echo.
echo [2/2] Cloning llama.cpp...
if exist "app\src\main\cpp\llama.cpp" (
    echo [SKIP] llama.cpp already exists
) else (
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "app\src\main\cpp\llama.cpp"
    echo [OK] llama.cpp cloned
)

echo.
echo ========================================
echo  Setup Complete!
echo ========================================
echo.
echo Next: Run download_models.bat to get model files
echo Then: Open project in Android Studio and build
echo.
pause
