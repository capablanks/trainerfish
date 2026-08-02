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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stockfish runner speaking UCI over JNI (in-process engine via libtrainerfish.so).
 */
object ProcEngine {

    private const val TAG = "TrainerFish-ProcEngine"

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    // True only while a 'go' search is considered active. Used to ignore late 'info' lines
    // from a previous position after we sent 'stop'.
    private val inSearch = AtomicBoolean(false)

    // Serializes TV position changes. A new UCI "position/go" command must not
    // overtake the bestmove that ends the previous search.
    private val evaluationMutex = Mutex()

    private var readerJob: Job? = null

    private val _scoreCp = MutableStateFlow<Int?>(null)
    val scoreCp: StateFlow<Int?> get() = _scoreCp

    /** The FEN most recently sent to the engine via [evaluateFen]. */
    private val _activeFen = MutableStateFlow<String?>(null)
    val activeFen: StateFlow<String?> get() = _activeFen

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> get() = _lines

    /** Clear the captured output lines + score without stopping the engine. */
    fun clearOutput() {
        inSearch.set(false)
        _scoreCp.value = null
        _lines.value = emptyList()
    }

    private fun resetState() {
        running.set(false)
        inSearch.set(false)
        inSearch.set(false)
        readerJob = null
        _scoreCp.value = null
        _activeFen.value = null
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
        _activeFen.value = null
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

        if (line.startsWith("bestmove")) {
            // Mark the end of the current search so late lines don't overwrite the next position.
            inSearch.set(false)
        }

        if (inSearch.get() && line.startsWith("info ")) {
            // In MultiPV mode, Stockfish emits one "info" line per PV. We only want PV1 for the eval bar.
            // Otherwise the last-arriving PV (often multipv 2/3/4) can overwrite the bar value and desync from the "Top lines" list.
            if (line.contains(" multipv ") && !line.contains(" multipv 1 ")) return

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

    private suspend fun stopSearchAndWaitLocked(timeoutMs: Long): Boolean {
        if (!running.get() || !inSearch.get()) return true

        send("stop")
        return withTimeoutOrNull(timeoutMs.coerceAtLeast(100L)) {
            while (running.get() && inSearch.get()) delay(5L)
            true
        } ?: !inSearch.get()
    }

    /**
     * Stop the active search and wait for its UCI bestmove before allowing a
     * caller to replace the position. This prevents overlapping live-TV
     * searches from racing inside Stockfish's PV formatter.
     */
    suspend fun stopSearchAndWait(timeoutMs: Long = 2_000L): Boolean {
        evaluationMutex.lock()
        return try {
            stopSearchAndWaitLocked(timeoutMs)
        } finally {
            evaluationMutex.unlock()
        }
    }

    /** Safely replace a live position with a short timed search. */
    suspend fun evaluateFenSafely(
        fen: String,
        movetimeMs: Int = 500,
        multiPv: Int = 1
    ): Boolean {
        evaluationMutex.lock()
        return try {
            if (!running.get() || !stopSearchAndWaitLocked(2_000L)) {
                false
            } else {
                setMultiPv(multiPv)
                evaluateFen(fen, movetimeMs)
                true
            }
        } finally {
            evaluationMutex.unlock()
        }
    }

    /** Safely replace a live position with a fixed-depth search. */
    suspend fun evaluateFenDepthSafely(
        fen: String,
        depthMax: Int = 60,
        multiPv: Int = 1
    ): Boolean {
        evaluationMutex.lock()
        return try {
            if (!running.get() || !stopSearchAndWaitLocked(2_000L)) {
                false
            } else {
                setMultiPv(multiPv)
                evaluateFenDepth(fen, depthMax)
                true
            }
        } finally {
            evaluationMutex.unlock()
        }
    }

    /** Quick evaluation used by UI. */
    fun evaluateFen(fen: String, movetimeMs: Int = 500) {
        if (!running.get()) return
        // Track what position the currently-running search belongs to.
        // BeatFishScreen uses this to normalize the eval bar into White POV
        // without any race on UI state.
        _activeFen.value = fen
        // Important: prevent stale info lines from a previous search overwriting the score
        // for the new position.
        inSearch.set(false)
        _scoreCp.value = null
        _lines.value = emptyList()

        send("stop")
        send("ucinewgame")
        send("position fen $fen")
        inSearch.set(true)
        send("go movetime $movetimeMs")
    }

    /** Evaluation capped by a fixed depth (safer than long movetime searches for PGN mode). */
    fun evaluateFenDepth(fen: String, depthMax: Int = 60) {
        if (!running.get()) return
        _activeFen.value = fen
        inSearch.set(false)
        _scoreCp.value = null
        _lines.value = emptyList()

        send("stop")
        send("ucinewgame")
        send("position fen $fen")
        inSearch.set(true)
        val d = depthMax.coerceIn(1, 80)
        send("go depth $d")
    }


}
