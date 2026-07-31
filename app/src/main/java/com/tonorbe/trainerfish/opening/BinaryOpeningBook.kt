// BinaryOpeningBook.kt (DROP-IN REPLACEMENT)
package com.tonorbe.trainerfish.opening

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream

/**
 * One move from the binary opening book.
 *
 * Squares are 0..63 in TrainerFish orientation:
 *   0 = a1, 7 = h1, 56 = a8, 63 = h8
 *
 * promo = 0 (no promotion), 1=Q, 2=R, 3=B, 4=N
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

object BinaryOpeningBook {

    @Volatile
    private var loaded: Boolean = false

    @Volatile
    private var keys: LongArray = LongArray(0)

    @Volatile
    private var offsets: LongArray = LongArray(0)

    @Volatile
    private var data: ByteArray = ByteArray(0)

    /** True if a book is loaded. */
    val isLoaded: Boolean
        get() = loaded

    // Ensures only one loader runs even if called from multiple screens.
    private val loadMutex = Mutex()

    /**
     * Load the unified book from raw resources (idempotent).
     * Runs on Dispatchers.IO and is guarded by [loadMutex].
     */
    suspend fun loadIfNeeded(
        context: Context,
        @RawRes dataRes: Int,
        @RawRes indexRes: Int
    ) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            loadMutex.withLock {
                if (loaded) return@withLock
                loadFromRaw(context, dataRes, indexRes)
                loaded = true
            }
        }
    }

    private fun loadFromRaw(
        context: Context,
        @RawRes dataRes: Int,
        @RawRes indexRes: Int
    ) {
        val res = context.resources

        // Data blob
        val dataBytes = res.openRawResource(dataRes).use { input ->
            BufferedInputStream(input).readBytes()
        }

        // Index: (key:Long, offset:Long) * N
        val indexBytes = res.openRawResource(indexRes).use { input ->
            BufferedInputStream(input).readBytes()
        }

        val entryCount = indexBytes.size / 16
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
    }

    /**
     * Look up book moves for a FEN. Returns null if not in book (or not loaded).
     *
     * Key hashing matches the Python builder: first 3 FEN fields ("board side castling").
     */
    fun lookup(fen: String): BookPosition? {
        if (!loaded) return null

        val key = fenToKey(fen)
        val idx = findKeyIndexUnsigned(key)
        if (idx < 0) return null

        val offset = offsets[idx].toInt()
        val buf = data

        var p = offset
        val storedKey = readLongLE(buf, p); p += 8
        if (storedKey != key) return null

        val totalCount = readIntLE(buf, p); p += 4
        val moveCount = readIntLE(buf, p); p += 4

        val mc = moveCount.coerceAtLeast(0)
        val moves = ArrayList<BookMove>(mc)
        repeat(mc) {
            val from = readShortLE(buf, p); p += 2
            val to = readShortLE(buf, p); p += 2
            val promo = buf[p].toInt() and 0xFF; p += 1
            /* flags (reserved) */ p += 1
            val cnt = readIntLE(buf, p); p += 4
            moves.add(BookMove(from = from, to = to, promo = promo, count = cnt))
        }

        return BookPosition(key = key, totalCount = totalCount, moves = moves)
    }

    fun unload() {
        keys = LongArray(0)
        offsets = LongArray(0)
        data = ByteArray(0)
        loaded = false
    }

    // ---------------- helpers ----------------

    private fun fenToKey(fen: String): Long {
        val parts = fen.trim().split(Regex("\\s+"))
        if (parts.size < 3) return 0L
        val epd = "${parts[0]} ${parts[1]} ${parts[2]}"
        val bytes = epd.toByteArray(Charsets.UTF_8)

        var h = 0xcbf29ce484222325uL // FNV-1a offset
        val prime = 0x100000001b3uL  // FNV-1a prime
        for (b in bytes) {
            val v = (b.toInt() and 0xFF).toULong()
            h = h xor v
            h *= prime
        }
        return h.toLong()
    }

    private fun findKeyIndexUnsigned(key: Long): Int {
        if (!loaded || keys.isEmpty()) return -1
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi).ushr(1)
            val cmp = java.lang.Long.compareUnsigned(keys[mid], key)
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
}
