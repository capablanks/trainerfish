// sf_entry.cpp
// Bridge between Stockfish main() and our JNI wrapper.
// We compile main.cpp as stockfish_main_cpp (C++ linkage),
// then expose a C-linkage wrapper stockfish_main() that JNI calls.

#define main stockfish_main_cpp
#include "stockfish_src/main.cpp"   // adjust path if your main.cpp lives elsewhere
#undef main

// Exported symbol with C linkage that native_stockfish.cpp expects
extern "C" int stockfish_main(int argc, char** argv) {
    return stockfish_main_cpp(argc, argv);
}

