// app/src/main/java/com/tonorbe/trainerfish/opening/OpeningArrows.kt
package com.tonorbe.trainerfish.opening

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Simple spec for a book arrow: from square -> to square,
 * with a weight (0..1) and a display color.
 */
private data class ArrowSpec(
    val fromSq: Int,
    val toSq: Int,
    val weight: Float,
    val color: Color
)

/**
 * Build arrows from a BookPosition (binary book).
 * We take the top few moves by count and map them to colors / thickness.
 */
private fun buildArrowsFromBook(pos: BookPosition): List<ArrowSpec> {
    if (pos.moves.isEmpty()) return emptyList()

    // Sort by popularity (count) descending
    val sorted = pos.moves.sortedByDescending { it.count }

    // Show at most 4 arrows to avoid clutter
    val top = sorted.take(4)
    val maxCnt = top.maxOf { it.count }.coerceAtLeast(1)

    // Nice high-contrast colors for the top few moves
    val palette = listOf(
        Color(0xFFFF00FF), // pink
        Color(0xFFFFC107), // amber
        Color(0xFF3F51B5), // indigo
        Color(0xFF4CAF50)  // green
    )

    return top.mapIndexed { idx, m ->
        val baseWeight = m.count.toFloat() / maxCnt.toFloat()
        val weight = baseWeight.coerceIn(0.25f, 1f) // never too thin

        ArrowSpec(
            fromSq = m.from,
            toSq = m.to,
            weight = weight,
            color = palette[idx.coerceAtMost(palette.lastIndex)]
        )
    }
}

/**
 * Map a square (file, rank) to a pixel center inside the board.
 *
 * Square indices use chesslib style: 0 = a1, 7 = h1, 56 = a8, 63 = h8.
 * Canvas coordinates: (0,0) is top-left, y grows downward.
 */
private fun squareCenter(
    file: Int,
    rank: Int,
    whiteBottom: Boolean,
    squareSize: Float
): Offset {
    // Flip horizontally if Black is at the bottom
    val screenFile = if (whiteBottom) file else 7 - file

    // Flip vertically if White is at the bottom (so rank 0 is at the bottom)
    val screenRank = if (whiteBottom) 7 - rank else rank

    val x = (screenFile + 0.5f) * squareSize
    val y = (screenRank + 0.5f) * squareSize
    return Offset(x, y)
}

/**
 * Draw a set of arrows on the board.
 */
private fun DrawScope.drawArrows(
    arrows: List<ArrowSpec>,
    squareSize: Float,
    whiteBottom: Boolean
) {
    for (a in arrows) {
        val fromFile = a.fromSq % 8
        val fromRank = a.fromSq / 8
        val toFile = a.toSq % 8
        val toRank = a.toSq / 8

        val start = squareCenter(fromFile, fromRank, whiteBottom, squareSize)
        val end = squareCenter(toFile, toRank, whiteBottom, squareSize)

        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 1f) continue

        val dirX = dx / len
        val dirY = dy / len

        val headSize = squareSize * 0.6f
        val shaftEnd = Offset(
            end.x - dirX * headSize * 0.6f,
            end.y - dirY * headSize * 0.6f
        )

        // Thickness scales with popularity
        val strokeWidth = squareSize * 0.20f * a.weight

        // Shaft
        drawLine(
            color = a.color,
            start = start,
            end = shaftEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Arrow head
        val angle = atan2(dy, dx)
        val headRadius = headSize * 0.5f
        val angle1 = angle + 0.9f
        val angle2 = angle - 0.9f

        val p1 = Offset(
            x = end.x - cos(angle1) * headRadius,
            y = end.y - sin(angle1) * headRadius
        )
        val p2 = Offset(
            x = end.x - cos(angle2) * headRadius,
            y = end.y - sin(angle2) * headRadius
        )

        val path = Path().apply {
            moveTo(end.x, end.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            close()
        }

        drawPath(
            path = path,
            color = a.color
        )
    }
}

/**
 * Overlay composable for drawing opening-book arrows over the board.
 *
 * `bookPos` comes from BinaryOpeningBook.lookup(currentFen)
 */
@Composable
fun OpeningArrowsOverlay(
    bookPos: BookPosition?,
    whiteBottom: Boolean,
    modifier: Modifier = Modifier
) {
    if (bookPos == null || bookPos.moves.isEmpty()) return

    val arrows = remember(bookPos) {
        buildArrowsFromBook(bookPos)
    }
    if (arrows.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val squareSize = min(size.width, size.height) / 8f
        drawArrows(arrows, squareSize, whiteBottom)
    }
}
