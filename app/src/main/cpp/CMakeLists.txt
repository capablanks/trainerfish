cmake_minimum_required(VERSION 3.22)
project(trainerfish_jni CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Stockfish 15.1 source tree
set(SF_DIR ${CMAKE_CURRENT_SOURCE_DIR}/stockfish_src)

file(GLOB_RECURSE SF_SOURCES
        ${SF_DIR}/*.cpp
        ${SF_DIR}/*.cc)

# Exclude JNI and main()
list(FILTER SF_SOURCES EXCLUDE REGEX ".*/native_stockfish\\.cpp$")
list(FILTER SF_SOURCES EXCLUDE REGEX ".*/sf_entry\\.cpp$")
list(FILTER SF_SOURCES EXCLUDE REGEX "(.*/)?main\\.cpp$")

add_library(trainerfish SHARED
        native_stockfish.cpp
        sf_entry.cpp
        ${SF_SOURCES}
)

target_include_directories(trainerfish PRIVATE
        ${SF_DIR}
        ${SF_DIR}/nnue
        ${SF_DIR}/syzygy
        ${SF_DIR}/incbin
)

# NNUE disabled (classical SF only)
target_compile_definitions(trainerfish PRIVATE NNUE_EMBEDDING_OFF=1)

# Core defines for Android release-ish build
target_compile_definitions(trainerfish PRIVATE ANDROID NDEBUG)

# ---- Common optimizations (safe across ABIs) ----
target_compile_options(trainerfish PRIVATE
        -O3
        -ffast-math
        -funroll-loops
)

# ---- ABI-specific flags ----
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    # 32-bit ARM: NEON + softfp, plus atomic library is often needed
    target_compile_options(trainerfish PRIVATE
            -march=armv7-a
            -mfpu=neon
            -mfloat-abi=softfp
    )
    target_link_libraries(trainerfish atomic)

elseif (ANDROID_ABI STREQUAL "arm64-v8a")
    # arm64: NDK already targets armv8-a; no extra -march needed.
    # (Keep clean for maximum compatibility.)
elseif (ANDROID_ABI STREQUAL "x86_64")
    # x86_64: no extra flags needed in most cases
    # Optional (only if you want/need it):
    # target_compile_options(trainerfish PRIVATE -msse4.1)
endif()

# core Android log lib
target_link_libraries(trainerfish log)

# Android 15+ / modern devices: ensure 16 KB alignment for 64-bit ABIs
# (Apply to arm64-v8a AND x86_64)
if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(trainerfish PRIVATE "-Wl,-z,max-page-size=16384")
endif()