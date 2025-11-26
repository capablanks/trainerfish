package com.tonorbe.trainerfish.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stockfish runner speaking UCI over JNI (in-process engine via libtrainerfish.so).
 */
object ProcEngine {

    private const val TAG = "TrainerFish-ProcEngine"

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    private var readerJob: Job? = null

    private val _scoreCp = MutableStateFlow<Int?>(null)
    val scoreCp: StateFlow<Int?> get() = _scoreCp

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> get() = _lines

    private fun resetState() {
        running.set(false)
        readerJob = null
        _scoreCp.value = null
        _lines.value = emptyList()
    }

    /**
     * Start the JNI-based engine if not already running.
     */
    fun start(execPath: String) {
        if (running.get()) {
            Log.d(TAG, "start() called but engine already running")
            return
        }

        Log.d(TAG, "Starting JNI engine (ignoring execPath='$execPath')")

        // Reset Kotlin-side state only
        resetState()

        try {
            NativeStockfish.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start NativeStockfish", t)
            throw t
        }

        running.set(true)

        // --- UCI handshake ---
        send("uci")
        // SF 15.1: explicitly disable NNUE, run classical eval
        send("setoption name Use NNUE value false")
        send("isready")

        // Background reader loop
        readerJob = scope.launch {
            try {
                while (isActive && running.get()) {
                    val line = try {
                        NativeStockfish.poll()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error while polling engine", t)
                        null
                    }

                    if (line == null) {
                        delay(5L)
                        continue
                    }

                    handleLine(line)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in JNI engine reader", t)
            } finally {
                Log.d(TAG, "JNI engine reader finishing")
                resetState()
            }
        }
    }

    /**
     * Stop the engine and clear state.
     */
    fun stop() {
        Log.d(TAG, "stop() requested")
        if (!running.get()) {
            resetState()
            return
        }

        running.set(false)

        // Stop the reader coroutine
        try {
            readerJob?.cancel()
        } catch (_: Throwable) {
        }
        readerJob = null

        // Ask native layer to stop and join the Stockfish thread.
        try {
            NativeStockfish.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error while stopping NativeStockfish", t)
        }

        _scoreCp.value = null
        _lines.value = emptyList()
    }

    /** Send a UCI command if engine is running. */
    @Synchronized
    fun send(cmd: String) {
        if (!running.get()) return
        try {
            NativeStockfish.send(cmd)
            Log.v(TAG, ">> $cmd")
        } catch (t: Throwable) {
            Log.w(TAG, "Error while sending to JNI engine; marking as not running", t)
            running.set(false)
        }
    }

    /** Handle one line of engine output. */
    private fun handleLine(line: String) {
        Log.v(TAG, "<< $line")

        val prev = _lines.value
        val next = if (prev.size >= 200) {
            prev.drop(prev.size - 199) + line
        } else {
            prev + line
        }
        _lines.value = next

        if (line.startsWith("info ")) {
            val cp = parseCp(line)
            if (cp != null) {
                _scoreCp.value = cp
            }
        }
    }

    private fun parseCp(line: String): Int? {
        val regex = Regex("\\bscore\\s+cp\\s+(-?\\d+)")
        val match = regex.find(line) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    /** Set the engine MultiPV option (number of best lines to calculate). */
    fun setMultiPv(lines: Int) {
        if (!running.get()) return
        val n = lines.coerceIn(1, 4)   // we only support 1..4 from the UI
        send("setoption name MultiPV value $n")
    }

    /** Quick evaluation used by UI. */
    fun evaluateFen(fen: String, movetimeMs: Int = 500) {
        if (!running.get()) return
        send("stop")
        send("ucinewgame")
        send("position fen $fen")
        send("go movetime $movetimeMs")
    }

}
