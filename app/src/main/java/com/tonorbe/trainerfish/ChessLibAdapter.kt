package com.tonorbe.trainerfish

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.Piece as LibPiece

/** Convert a chesslib Board into our UI Piece[] array. */
fun boardToUiPieces(board: Board): Array<Piece?> {
    val ui = Array<Piece?>(64) { null }

    for (sq in Square.values()) {
        if (sq == Square.NONE) continue
        val p = board.getPiece(sq)
        if (p != LibPiece.NONE) {
            val idx = squareToIndex(sq)
            ui[idx] = p.toUiPiece()
        }
    }
    return ui
}

private fun squareToIndex(sq: Square): Int {
    val file = sq.file.ordinal  // 0..7  (A..H)
    val rank = sq.rank.ordinal  // 0..7  (1..8)
    return rank * 8 + file      // A1 = 0
}

private fun LibPiece.toUiPiece(): Piece? = when (this) {
    LibPiece.WHITE_PAWN   -> Piece(PieceType.PAWN, true)
    LibPiece.WHITE_KNIGHT -> Piece(PieceType.KNIGHT, true)
    LibPiece.WHITE_BISHOP -> Piece(PieceType.BISHOP, true)
    LibPiece.WHITE_ROOK   -> Piece(PieceType.ROOK, true)
    LibPiece.WHITE_QUEEN  -> Piece(PieceType.QUEEN, true)
    LibPiece.WHITE_KING   -> Piece(PieceType.KING, true)
    LibPiece.BLACK_PAWN   -> Piece(PieceType.PAWN, false)
    LibPiece.BLACK_KNIGHT -> Piece(PieceType.KNIGHT, false)
    LibPiece.BLACK_BISHOP -> Piece(PieceType.BISHOP, false)
    LibPiece.BLACK_ROOK   -> Piece(PieceType.ROOK, false)
    LibPiece.BLACK_QUEEN  -> Piece(PieceType.QUEEN, false)
    LibPiece.BLACK_KING   -> Piece(PieceType.KING, false)
    else -> null
}
