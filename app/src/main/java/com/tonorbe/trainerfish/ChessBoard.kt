@file:Suppress("SpellCheckingInspection")

package com.tonorbe.trainerfish

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PieceStyle { Solid, Outline }

/**
 * Draws an 8×8 board.
 *
 * The `board` array is already oriented for display:
 * - if `whiteBottom = true`, index 0 is a1 (bottom-left)
 * - if `whiteBottom = false`, index 0 is h8 (bottom-left)
 */
@Composable
fun ChessBoard(
    board: Array<Piece?>,
    selected: Int?,
    lastMoveFrom: Int? = null,
    lastMoveTo: Int? = null,
    onSquareClick: (Int) -> Unit,
    light: Color = Color(0xFFEEEED2),
    dark: Color = Color(0xFF769656),
    pieceStyle: PieceStyle = PieceStyle.Solid,
    whiteBottom: Boolean = true
) {
    // Selection + last-move highlight
    val selColor = Color(0x88FFD54F)
    val lastMoveColor = Color(0x66FFF176)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        // Ranks from top (7) to bottom (0) on screen
        for (rank in 7 downTo 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Files from left (0) to right (7) on screen
                for (file in 0..7) {
                    val idx = rank * 8 + file

                    // Base square color pattern
                    val baseIsDark = ((rank + file) % 2 == 0)
                    val baseColor = if (baseIsDark) dark else light

                    val isSelected = (selected == idx)
                    val isLastMove =
                        (lastMoveFrom != null && lastMoveFrom == idx) ||
                                (lastMoveTo != null && lastMoveTo == idx)

                    val bg = when {
                        isSelected -> selColor
                        isLastMove -> lastMoveColor
                        else       -> baseColor
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(bg)
                            .clickable { onSquareClick(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Piece
                        board.getOrNull(idx)?.let { p ->
                            drawPiece(p, pieceStyle)
                        }

                        // ---------- Coordinates overlay ----------

                        // Map visual index -> logical square index (a1 from White's POV)
                        val logicalIdx = if (whiteBottom) idx else 63 - idx
                        val logicalFile = logicalIdx % 8          // 0..7 for a..h
                        val logicalRank = logicalIdx / 8          // 0..7 for 1..8
                        val fileChar = ('a' + logicalFile).toString()
                        val rankChar = (logicalRank + 1).toString()

                        // Contrast color: opposite of the *base* square color,
                        // so it adapts to Night / Blue / etc themes.
                        val coordColor = if (baseIsDark) light else dark

                        // Very small padding so the text hugs the corners
                        val pad = 1.dp

                        // File letter on the bottom edge (screen-bottom rank)
                        if (rank == 0) {
                            Text(
                                text = fileChar,
                                fontSize = 10.sp,
                                color = coordColor,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = pad, bottom = pad)
                            )
                        }

                        // Rank number on the left edge (screen-left file)
                        if (file == 0) {
                            Text(
                                text = rankChar,
                                fontSize = 10.sp,
                                color = coordColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = pad, top = pad)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun drawPiece(p: Piece, style: PieceStyle) {
    if (style == PieceStyle.Solid) {
        val resId = when (p.type) {
            PieceType.KING   -> if (p.isWhite) R.drawable.cburnett_wk else R.drawable.cburnett_bk
            PieceType.QUEEN  -> if (p.isWhite) R.drawable.cburnett_wq else R.drawable.cburnett_bq
            PieceType.ROOK   -> if (p.isWhite) R.drawable.cburnett_wr else R.drawable.cburnett_br
            PieceType.BISHOP -> if (p.isWhite) R.drawable.cburnett_wb else R.drawable.cburnett_bb
            PieceType.KNIGHT -> if (p.isWhite) R.drawable.cburnett_wn else R.drawable.cburnett_bn
            PieceType.PAWN   -> if (p.isWhite) R.drawable.cburnett_wp else R.drawable.cburnett_bp
        }
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
            return
        }
    }

    // Outline / glyph fallback
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = p.glyph,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            color = if (p.isWhite) Color.Black else Color.White
        )
        Text(
            text = p.glyph,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            color = if (p.isWhite) Color.White else Color.Black
        )
    }
}
