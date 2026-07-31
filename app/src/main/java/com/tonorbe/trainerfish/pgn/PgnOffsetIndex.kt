package com.tonorbe.trainerfish.pgn

import android.content.Context
import androidx.annotation.RawRes
import java.io.DataInputStream
import kotlin.math.min

data class PgnOffset(val start: Int, val length: Int)

class PgnOffsetIndex(private val offsets: IntArray) {
    val size: Int get() = offsets.size / 2

    fun get(idx: Int): PgnOffset? {
        val i = idx * 2
        if (i < 0 || i + 1 >= offsets.size) return null
        val start = offsets[i]
        val len = offsets[i + 1]
        if (start < 0 || len <= 0) return null
        return PgnOffset(start, len)
    }

    companion object {
        fun load(context: Context, @RawRes resId: Int): PgnOffsetIndex {
            context.resources.openRawResource(resId).use { input ->
                val din = DataInputStream(input)
                val count = din.readIntLE()
                val arr = IntArray(count * 2)
                for (i in 0 until count) {
                    arr[i * 2] = din.readIntLE()
                    arr[i * 2 + 1] = din.readIntLE()
                }
                return PgnOffsetIndex(arr)
            }
        }

        private fun DataInputStream.readIntLE(): Int {
            val b1 = read()
            val b2 = read()
            val b3 = read()
            val b4 = read()
            if ((b1 or b2 or b3 or b4) < 0) throw java.io.EOFException()
            return (b1 and 0xFF) or ((b2 and 0xFF) shl 8) or ((b3 and 0xFF) shl 16) or ((b4 and 0xFF) shl 24)
        }
    }
}
