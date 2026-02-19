cmake_minimum_required(VERSION 3.22.1)
project("arm_translator" CXX C)

# Enable NEON optimizations for Arm
add_compile_options(-fno-fast-math -fno-finite-math-only)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -std=c++17")

# ===============================================
# whisper.cpp - Speech-to-Text
# ===============================================
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    # Use whisper.cpp's own build system
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    add_subdirectory(${WHISPER_DIR} whisper_build)
    set(WHISPER_AVAILABLE TRUE)
    message(STATUS "whisper.cpp found, building with add_subdirectory()")
else()
    set(WHISPER_AVAILABLE FALSE)
    message(WARNING "whisper.cpp not found at ${WHISPER_DIR}")
endif()

# ===============================================
# llama.cpp - LLM Translation
# ===============================================
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

if(EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    # Use llama.cpp's own build system
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    add_subdirectory(${LLAMA_DIR} llama_build)
    set(LLAMA_AVAILABLE TRUE)
    message(STATUS "llama.cpp found, building with add_subdirectory()")
else()
    set(LLAMA_AVAILABLE FALSE)
    message(WARNING "llama.cpp not found at ${LLAMA_DIR}")
endif()

# ===============================================
# Main JNI library
# ===============================================
add_library(arm_translator SHARED
    whisper_jni.cpp
    llama_jni.cpp
    tts_jni.cpp
)

target_include_directories(arm_translator PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link system libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(arm_translator
    ${log-lib}
    ${android-lib}
)

if(WHISPER_AVAILABLE)
    target_include_directories(arm_translator PRIVATE
        ${WHISPER_DIR}/include
        ${WHISPER_DIR}/ggml/include
    )
    target_link_libraries(arm_translator whisper)
    target_compile_definitions(arm_translator PRIVATE HAS_WHISPER)
endif()

if(LLAMA_AVAILABLE)
    target_include_directories(arm_translator PRIVATE
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}/ggml/include
    )
    target_link_libraries(arm_translator llama)
    target_compile_definitions(arm_translator PRIVATE HAS_LLAMA)
endif()
