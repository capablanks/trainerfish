package com.tonorbe.trainerfish.engine

object NativeStockfish {

    init {
        // Name must match add_library(trainerfish SHARED ...) in CMakeLists.txt
        System.loadLibrary("trainerfish")
    }

    @JvmStatic external fun start()
    @JvmStatic external fun stop()
    @JvmStatic external fun send(cmd: String)
    @JvmStatic external fun poll(): String?
}
