cmake_minimum_required(VERSION 3.22)
project(trainerfish_jni CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Stockfish 15.1 source tree lives here:
set(SF_DIR ${CMAKE_CURRENT_SOURCE_DIR}/stockfish_src)

# Grab all Stockfish .cpp / .cc files recursively
file(GLOB_RECURSE SF_SOURCES
        ${SF_DIR}/*.cpp
        ${SF_DIR}/*.cc
)

# Exclude our JNI / wrapper sources and the original main()
list(FILTER SF_SOURCES EXCLUDE REGEX ".*/native_stockfish\\.cpp$")
list(FILTER SF_SOURCES EXCLUDE REGEX ".*/sf_entry\\.cpp$")
list(FILTER SF_SOURCES EXCLUDE REGEX "(.*/)?main\\.cpp$")

# Build shared library "trainerfish"
add_library(trainerfish SHARED
        native_stockfish.cpp
        sf_entry.cpp
        ${SF_SOURCES}
)

# Include dirs for Stockfish headers and NNUE/Syzygy helpers
target_include_directories(trainerfish PRIVATE
        ${SF_DIR}
        ${SF_DIR}/nnue
        ${SF_DIR}/syzygy
        ${SF_DIR}/incbin
)

# OPTIONAL: you can keep this to avoid embedded NNUE,
# but SF 15.1 will still run classical even if NNUE is off.
target_compile_definitions(trainerfish PRIVATE NNUE_EMBEDDING_OFF=1)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(trainerfish PRIVATE -O3 -ffast-math -funroll-loops -march=armv8-a)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(trainerfish PRIVATE -O3 -ffast-math -funroll-loops -march=armv7-a -mfpu=neon -mfloat-abi=softfp)
    target_link_libraries(trainerfish atomic)
endif()

target_compile_definitions(trainerfish PRIVATE -DANDROID -DNDEBUG)
target_link_libraries(trainerfish log)

# Ensure 16 KB page size alignment for Android 15+ (arm64)
if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_link_options(trainerfish PRIVATE
            "-Wl,-z,max-page-size=16384"
    )
endif()
