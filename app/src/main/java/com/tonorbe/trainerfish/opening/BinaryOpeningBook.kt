// app/src/main/java/com/tonorbe/trainerfish/opening/BinaryOpeningBook.kt
package com.tonorbe.trainerfish.opening

import android.content.Context
import androidx.annotation.RawRes
import com.tonorbe.trainerfish.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream

/**
 * Which opening **book pack** is loaded:
 *  - E4     -> 1.e4 games  (fullbook_d26_e4_*.bin)
 *  - D4     -> 1.d4 games  (fullbook_d26_d4_*.bin)
 *  - OTHERS -> everything else (fullbook_d26_others_*.bin)
 *
 * NOTE: This enum used to live in OpeningTree.kt. We keep it here now so the
 * opening explorer can rely only on the binary books.
 */
enum class OpeningPack {
    E4,
    D4,
    OTHERS
}

/**
 * One move from the binary opening book.
 *
 * Squares are 0..63 in chesslib / TrainerFish orientation:
 *   0 = a1, 7 = h1, 56 = a8, 63 = h8
 *
 * promo = 0 (no promotion), 1=Q, 2=R, 3=B, 4=N (same mapping as the Python builder).
 */
data class BookMove(
    val from: Int,
    val to: Int,
    val promo: Int,
    val count: Int
)

/** Aggregated data for a single position. */
data class BookPosition(
    val key: Long,
    val totalCount: Int,
    val moves: List<BookMove>
)

/**
 * Loader for the compact binary opening books produced by `build_split_book_from_pgns.py`.
 *
 * Files (in res/raw), for max depth = 26 plies:
 *
 *   fullbook_d26_e4_book_data.bin     -> R.raw.fullbook_d26_e4_book_data
 *   fullbook_d26_e4_book_index.bin    -> R.raw.fullbook_d26_e4_book_index
 *   fullbook_d26_d4_book_data.bin     -> R.raw.fullbook_d26_d4_book_data
 *   fullbook_d26_d4_book_index.bin    -> R.raw.fullbook_d26_d4_book_index
 *   fullbook_d26_others_book_data.bin -> R.raw.fullbook_d26_others_book_data
 *   fullbook_d26_others_book_index.bin-> R.raw.fullbook_d26_others_book_index
 *
 * Layout:
 *   index: repeated records  [ key(Q, LE), offset(Q, LE) ]
 *   data:  repeated records
 *           [ key(Q, LE), totalCount(I, LE), moveCount(I, LE),
 *             move * moveCount ]
 *          where each move is:
 *           [ from(H, LE), to(H, LE), promo(B), flags(B, reserved=0), count(I, LE) ]
 *
 * Keys are FNV-1a 64-bit hashes of the first 3 FEN fields (board, side, castling),
 * sorted as **unsigned** 64-bit integers.
 */
object BinaryOpeningBook {

    @Volatile
    private var loaded: Boolean = false

    // In-memory index
    @Volatile
    private var keys: LongArray = LongArray(0)

    @Volatile
    private var offsets: LongArray = LongArray(0)

    // Raw data blob
    @Volatile
    private var data: ByteArray = ByteArray(0)

    @Volatile
    private var loadedPack: OpeningPack? = null

    /** Which pack (E4 / D4 / OTHERS) is currently in memory, if any. */
    val currentPack: OpeningPack?
        get() = loadedPack

    /** True if some book is loaded at all. */
    val isLoaded: Boolean
        get() = loaded

    // -------------------------------------------------------------------------
    // Loading API
    // -------------------------------------------------------------------------

    /**
     * Load a specific pack (E4 / D4 / OTHERS) into memory if needed.
     *
     * If the same pack is already loaded, this is a no-op.
     * If a different pack is loaded, we fully replace it.
     */
    suspend fun loadIfNeeded(
        context: Context,
        pack: OpeningPack
    ) {
        if (loaded && loadedPack == pack) return

        // Single all-in-one 32-ply book
        val dataRes = R.raw.fullbook_d32_all_book_data
        val indexRes = R.raw.fullbook_d32_all_book_index

        withContext(Dispatchers.IO) {
            loadFromRaw(context, dataRes, indexRes)
            loadedPack = pack   // we still remember which “pack” the UI thinks is active
        }
    }


    /**
     * Legacy helper: load from explicit raw resource ids.
     * This always reloads and clears [loadedPack] (callers are opting out of packs).
     */
    suspend fun loadIfNeeded(
        context: Context,
        @RawRes dataRes: Int,
        @RawRes indexRes: Int
    ) {
        withContext(Dispatchers.IO) {
            loadFromRaw(context, dataRes, indexRes)
            loadedPack = null
        }
    }

    private fun loadFromRaw(
        context: Context,
        @RawRes dataRes: Int,
        @RawRes indexRes: Int
    ) {
        val res = context.resources

        // ----- Load data file -----
        val dataBytes = res.openRawResource(dataRes).use { input ->
            BufferedInputStream(input).readBytes()
        }

        // ----- Load index file -----
        val indexBytes = res.openRawResource(indexRes).use { input ->
            BufferedInputStream(input).readBytes()
        }

        val entryCount = indexBytes.size / (8 + 8) // key(Q) + offset(Q)
        val kArr = LongArray(entryCount)
        val offArr = LongArray(entryCount)

        var p = 0
        for (i in 0 until entryCount) {
            val key = readLongLE(indexBytes, p); p += 8
            val off = readLongLE(indexBytes, p); p += 8
            kArr[i] = key
            offArr[i] = off
        }

        keys = kArr
        offsets = offArr
        data = dataBytes
        loaded = true
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Look up book moves for a FEN.
     *
     * Returns null if this position is not in the book.
     *
     * NOTE: The key computation **must** match the Python builder's `fen_to_key`.
     * That function hashes only the first 3 FEN fields: "board side castling".
     */
    fun lookup(fen: String): BookPosition? {
        if (!loaded) return null

        val key = fenToKey(fen)
        val idx = findKeyIndexUnsigned(key)
        if (idx < 0) return null

        val offset = offsets[idx].toInt()  // book files are << 2GB, Int is safe
        val buf = data
        var p = offset

        // Read header from data blob
        val storedKey = readLongLE(buf, p); p += 8
        if (storedKey != key) {
            // Hash collision or mismatch; treat as "not found" to be safe.
            return null
        }
        val totalCount = readIntLE(buf, p); p += 4
        val moveCount = readIntLE(buf, p); p += 4

        val mc = moveCount.coerceAtLeast(0)
        val moves = ArrayList<BookMove>(mc)
        repeat(mc) {
            val from = readShortLE(buf, p); p += 2
            val to = readShortLE(buf, p); p += 2
            val promo = buf[p].toInt() and 0xFF; p += 1
            /* val flags = */ p += 1 // currently unused, reserved
            val cnt = readIntLE(buf, p); p += 4

            moves.add(
                BookMove(
                    from = from,
                    to = to,
                    promo = promo,
                    count = cnt
                )
            )
        }

        return BookPosition(
            key = key,
            totalCount = totalCount,
            moves = moves
        )
    }

    /** Drop the current book from memory (optional). */
    fun unload() {
        keys = LongArray(0)
        offsets = LongArray(0)
        data = ByteArray(0)
        loaded = false
        loadedPack = null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Compute the same FNV-1a 64-bit key that the Python builder uses.
     *
     * Python reference (build_split_book_from_pgns.py):
     *
     *   FNV_OFFSET = 0xcbf29ce484222325
     *   FNV_PRIME  = 0x100000001b3
     *
     *   def fen_to_key(fen: str) -> int:
     *       parts = fen.strip().split()
     *       epd = " ".join(parts[:3])  # board, side, castling
     *       h = FNV_OFFSET
     *       for b in epd.encode("utf-8"):
     *           h ^= b
     *           h = (h * FNV_PRIME) & 0xFFFFFFFFFFFFFFFF
     *       return h
     */
    private fun fenToKey(fen: String): Long {
        // Take the first 3 FEN fields: board, side, castling.
        val parts = fen.trim().split(Regex("\\s+"))
        if (parts.size < 3) return 0L

        val epd = "${parts[0]} ${parts[1]} ${parts[2]}"
        val bytes = epd.toByteArray(Charsets.UTF_8)

        var h = 0xcbf29ce484222325uL       // FNV_OFFSET
        val prime = 0x100000001b3uL        // FNV_PRIME

        for (b in bytes) {
            val v = (b.toInt() and 0xFF).toULong()
            h = h xor v
            h *= prime
        }

        // lower 64 bits as signed Long (bit pattern matches Python).
        return h.toLong()
    }

    /**
     * Binary search on [keys] treating them as **unsigned** 64-bit integers.
     * The index files are sorted by unsigned key in Python.
     */
    private fun findKeyIndexUnsigned(key: Long): Int {
        if (!loaded || keys.isEmpty()) return -1

        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi).ushr(1)
            val midVal = keys[mid]
            val cmp = java.lang.Long.compareUnsigned(midVal, key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun readIntLE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLongLE(buf: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((buf[offset + i].toLong() and 0xFFL) shl (8 * i))
        }
        return result
    }

    // --- Debug helpers (you can keep or remove later) -------------------

    /** Compute key for a FEN without touching the book (for debugging). */
    fun debugKeyForFen(fen: String): Long = fenToKey(fen)

    /** Check if the current loaded book contains a given key. */
    fun debugHasKey(key: Long): Boolean {
        return findKeyIndexUnsigned(key) >= 0
    }
}
