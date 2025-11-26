package com.tonorbe.trainerfish.engine

/**
 * Legacy wrapper kept for compatibility with older code that referenced
 * `JniStockfishUci`. Internally it delegates everything to [ProcEngine],
 * which runs our in-process Stockfish (libtrainerfish.so).
 *
 * In most of the current code, we ignore the execPath and just run the
 * bundled JNI engine.
 */
object JniStockfishUci {

    /**
     * Start the engine. The execPath is kept only for backwards
     * compatibility and is ignored by [ProcEngine].
     */
    fun start(execPath: String = "inline") {
        ProcEngine.start(execPath)
    }

    /** Stop the engine and clear state. */
    fun stop() {
        ProcEngine.stop()
    }

    /** Send a raw UCI command to the engine (if running). */
    fun send(cmd: String) {
        ProcEngine.send(cmd)
    }

    /**
     * Convenience helper: ask the engine to evaluate a FEN once.
     * This is just a pass-through to [ProcEngine.evaluateFen].
     */
    fun evaluateFen(fen: String, movetimeMs: Int = 500) {
        ProcEngine.evaluateFen(fen, movetimeMs)
    }
}
