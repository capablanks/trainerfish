package com.tonorbe.trainerfish

import kotlin.math.abs
import kotlin.math.max

data class Move(val from: Int, val to: Int, val captured: Piece?, val moved: Piece)
enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

data class Piece(val type: PieceType, val isWhite: Boolean) {
    val glyph: String = when (type) {
        PieceType.KING   -> if (isWhite) "\u2654" else "\u265A"
        PieceType.QUEEN  -> if (isWhite) "\u2655" else "\u265B"
        PieceType.ROOK   -> if (isWhite) "\u2656" else "\u265C"
        PieceType.BISHOP -> if (isWhite) "\u2657" else "\u265D"
        PieceType.KNIGHT -> if (isWhite) "\u2658" else "\u265E"
        PieceType.PAWN   -> if (isWhite) "\u2659" else "\u265F"
    }
}

fun index(file: Int, rank: Int) = rank * 8 + file
fun fileOf(idx: Int) = idx % 8
fun rankOf(idx: Int) = idx / 8

class GameState(
    val board: Array<Piece?>,
    val whiteToMove: Boolean,
    private val history: List<Move>
) {
    fun tryMove(from: Int, to: Int): GameState? {
        val p = board[from] ?: return null
        if (p.isWhite != whiteToMove) return null
        if (!isBasicLegalMove(board, from, to)) return null

        val newBoard = board.copyOf()
        val captured = newBoard[to]
        newBoard[to] = p
        newBoard[from] = null
        val newHist = history + Move(from, to, captured, p)
        return GameState(newBoard, !whiteToMove, newHist)
    }

    fun undo(): GameState {
        if (history.isEmpty()) return this
        val last = history.last()
        val newBoard = board.copyOf()
        newBoard[last.from] = last.moved
        newBoard[last.to] = last.captured
        val newHist = history.dropLast(1)
        return GameState(newBoard, !whiteToMove, newHist)
    }

    companion object {
        fun newGame(): GameState {
            val b = Array<Piece?>(64) { null }
            fun backRank(rank: Int, white: Boolean) {
                b[index(0, rank)] = Piece(PieceType.ROOK, white)
                b[index(1, rank)] = Piece(PieceType.KNIGHT, white)
                b[index(2, rank)] = Piece(PieceType.BISHOP, white)
                b[index(3, rank)] = Piece(PieceType.QUEEN, white)
                b[index(4, rank)] = Piece(PieceType.KING, white)
                b[index(5, rank)] = Piece(PieceType.BISHOP, white)
                b[index(6, rank)] = Piece(PieceType.KNIGHT, white)
                b[index(7, rank)] = Piece(PieceType.ROOK, white)
            }
            backRank(0, true); backRank(7, false)
            for (f in 0..7) {
                b[index(f, 1)] = Piece(PieceType.PAWN, true)
                b[index(f, 6)] = Piece(PieceType.PAWN, false)
            }
            return GameState(b, true, emptyList())
        }
    }
}

fun isBasicLegalMove(board: Array<Piece?>, from: Int, to: Int): Boolean {
    if (from == to) return false
    val p = board[from] ?: return false
    val target = board[to]
    if (target?.isWhite == p.isWhite) return false

    val fx = fileOf(from); val fy = rankOf(from)
    val tx = fileOf(to);   val ty = rankOf(to)
    val dx = tx - fx;      val dy = ty - fy

    fun pathClear(stepX: Int, stepY: Int): Boolean {
        var x = fx + stepX; var y = fy + stepY
        while (x != tx || y != ty) {
            if (board[index(x, y)] != null) return false
            x += stepX; y += stepY
        }
        return true
    }

    return when (p.type) {
        PieceType.KNIGHT -> (dx*dx + dy*dy) == 5
        PieceType.BISHOP -> abs(dx) == abs(dy) && pathClear(dx.sign(), dy.sign())
        PieceType.ROOK   -> (dx == 0 || dy == 0) && pathClear(dx.sign(), dy.sign())
        PieceType.QUEEN  -> ((abs(dx) == abs(dy) || dx == 0 || dy == 0) && pathClear(dx.sign(), dy.sign()))
        PieceType.KING   -> max(abs(dx), abs(dy)) == 1
        PieceType.PAWN   -> {
            val dir = if (p.isWhite) 1 else -1
            val startRank = if (p.isWhite) 1 else 6
            if (dx == 0 && target == null) {
                if (dy == dir) return true
                if (fy == startRank && dy == 2*dir && board[index(fx, fy + dir)] == null) return true
            }
            if (abs(dx) == 1 && dy == dir && target != null) return true
            false
        }
    }
}

private fun Int.sign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}
