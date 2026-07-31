@file:Suppress("SpellCheckingInspection")

package com.tonorbe.trainerfish

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.core.graphics.PathParser
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


enum class PieceStyle { Solid, Outline }

data class PieceSetOption(val key: String, val label: String)

val TrainerFishPieceSets = listOf(
    PieceSetOption("original", "Original"),
    PieceSetOption("cburnett", "Cburnett SVG"),
    PieceSetOption("alpha", "Alpha"),
    PieceSetOption("california", "California"),
    PieceSetOption("chessnut", "Chessnut"),
    PieceSetOption("dubrovny", "Dubrovny"),
    PieceSetOption("maestro", "Maestro"),
    PieceSetOption("merida", "Merida"),
    PieceSetOption("pirouetti", "Pirouetti"),
    PieceSetOption("spatial", "Spatial")
)

fun trainerFishPieceSetLabel(key: String): String =
    TrainerFishPieceSets.firstOrNull { it.key == key }?.label ?: key.replaceFirstChar { it.uppercase() }


private fun isOriginalPieceSet(pieceSetKey: String): Boolean =
    pieceSetKey.trim().lowercase() in setOf("original", "classic", "drawable", "cburnett_png")

private fun originalPieceDrawableName(piece: Piece): String {
    val side = if (piece.isWhite) "w" else "b"
    val kind = when (piece.type) {
        PieceType.KING -> "k"
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        PieceType.PAWN -> "p"
    }
    return "cburnett_${side}${kind}"
}

private fun originalPieceDrawableId(context: android.content.Context, piece: Piece): Int =
    context.resources.getIdentifier(originalPieceDrawableName(piece), "drawable", context.packageName)

data class UserArrow(val from: Int, val to: Int)

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
    dark: Color = Color(0xFF8FB06B),
    pieceStyle: PieceStyle = PieceStyle.Solid,
    pieceSetKey: String = "original",
    whiteBottom: Boolean = true,
    engineHintFrom: Int? = null,
    engineHintTo: Int? = null
) {
    // Selection
    val selColor = Color(0x88FFD54F)
    val hintBorderColor = Color(0xFFE53935)   // red-ish outline for engine suggestion

    // --- User annotations (long-press highlights + arrows) ---
    val userHighlights = remember { mutableStateListOf<Int>() }
    val userArrows = remember { mutableStateListOf<UserArrow>() }

    // Clear user annotations whenever the *position* changes.
    // We don't have FEN here, so we derive a cheap hash from the visible 64-square array.
    // This updates when any move changes the piece placement.
    val positionHash = run {
        var h = 1
        for (i in 0 until 64) {
            h = 31 * h + (board.getOrNull(i)?.hashCode() ?: 0)
        }
        h
    }
    LaunchedEffect(positionHash) {
        userHighlights.clear()
        userArrows.clear()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun squareAt(pos: androidx.compose.ui.geometry.Offset): Int? {
                    val cell = (kotlin.math.min(size.width.toFloat(), size.height.toFloat()) / 8f).coerceAtLeast(1f)
                    val file = (pos.x / cell).toInt()
                    val rankFromTop = (pos.y / cell).toInt()
                    if (file !in 0..7 || rankFromTop !in 0..7) return null
                    val rank = 7 - rankFromTop // 0 bottom .. 7 top (visual)
                    return rank * 8 + file
                }

                detectTapGestures(
                    onTap = { pos ->
                        squareAt(pos)?.let { onSquareClick(it) }
                    },
                    onLongPress = { pos ->
                        squareAt(pos)?.let { sq ->
                            // Long-press on an *existing* annotation erases it.
                            // 1) If any arrow(s) start from this square, remove them.
                            // 2) Otherwise toggle square highlight.
                            val hasArrowFrom = userArrows.any { it.from == sq }
                            if (hasArrowFrom) {
                                for (i in userArrows.size - 1 downTo 0) {
                                    if (userArrows[i].from == sq) userArrows.removeAt(i)
                                }
                            } else {
                                if (userHighlights.contains(sq)) userHighlights.remove(sq) else userHighlights.add(sq)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                fun squareAt(pos: androidx.compose.ui.geometry.Offset): Int? {
                    val cell = (kotlin.math.min(size.width.toFloat(), size.height.toFloat()) / 8f).coerceAtLeast(1f)
                    val file = (pos.x / cell).toInt()
                    val rankFromTop = (pos.y / cell).toInt()
                    if (file !in 0..7 || rankFromTop !in 0..7) return null
                    val rank = 7 - rankFromTop
                    return rank * 8 + file
                }

                var startSq: Int? = null
                var endSq: Int? = null

                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        startSq = squareAt(pos)
                        endSq = startSq
                    },
                    onDrag = { change, _ ->
                        endSq = squareAt(change.position) ?: endSq
                    },
                    onDragEnd = {
                        val a = startSq
                        val b = endSq
                        if (a != null && b != null && a != b) {
                            userArrows.add(UserArrow(a, b))
                        }
                        startSq = null
                        endSq = null
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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

                        val isHintSquare =
                            (engineHintFrom != null && engineHintFrom == idx) ||
                                    (engineHintTo != null && engineHintTo == idx)

                        val bg = if (isSelected) selColor else baseColor

                        val baseModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(bg)

                        val squareModifier =
                            if (isHintSquare) {
                                baseModifier.border(2.dp, hintBorderColor)
                            } else {
                                baseModifier
                            }

                        Box(
                            modifier = squareModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            // User square highlight (long-press)
                            if (userHighlights.contains(idx)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF9E9E9E).copy(alpha = 0.35f))
                                )
                            }

                            // Piece
                            board.getOrNull(idx)?.let { p ->
                                DrawPiece(p, pieceStyle, pieceSetKey)
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

        // [OK] User-drawn arrows overlay (shades of gray, distinct from last-move arrow).
        if (userArrows.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = kotlin.math.min(size.width.toFloat(), size.height.toFloat()) / 8f

                fun centerOf(squareIdx: Int): Offset {
                    val file = squareIdx % 8
                    val rank = squareIdx / 8 // 0 bottom .. 7 top (visual)
                    val x = file * cell + cell / 2f
                    val y = (7 - rank) * cell + cell / 2f
                    return Offset(x, y)
                }

                val stroke = cell * 0.16f
                val color = Color(0xFF616161).copy(alpha = 0.55f)
                val headLen = cell * 0.40f

                userArrows.forEach { ar ->
                    if (ar.from == ar.to) return@forEach
                    val start = centerOf(ar.from)
                    val end = centerOf(ar.to)

                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val ux = dx / len
                    val uy = dy / len

                    val tailEnd = Offset(end.x - ux * headLen, end.y - uy * headLen)

                    drawLine(
                        color = color,
                        start = start,
                        end = tailEnd,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )

                    val perp = Offset(-uy, ux)
                    val left = Offset(
                        end.x - ux * headLen + perp.x * (headLen * 0.45f),
                        end.y - uy * headLen + perp.y * (headLen * 0.45f)
                    )
                    val right = Offset(
                        end.x - ux * headLen - perp.x * (headLen * 0.45f),
                        end.y - uy * headLen - perp.y * (headLen * 0.45f)
                    )

                    val path = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }

        // [OK] Last-move arrow overlay (neutral gray). We intentionally avoid square highlight.
        // Draws over the whole board, so it works for all modules (including Beat-the-Fish).
        // NOTE: The board array is already oriented for display.
        // However, callers often provide lastMoveFrom/To in *logical* coordinates
        // (a1=0 from White's POV). When the board is flipped, the last-move arrow
        // must flip too, otherwise it will appear mirrored.
        // Last-move indices are expected to be in DISPLAY coordinates
        // (same coordinate system as the `board` array passed into ChessBoard).
        val arrowFrom = lastMoveFrom
        val arrowTo = lastMoveTo
        if (arrowFrom != null && arrowTo != null && arrowFrom != arrowTo) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = kotlin.math.min(size.width.toFloat(), size.height.toFloat()) / 8f

                fun centerOf(squareIdx: Int): Offset {
                    val file = squareIdx % 8
                    val rank = squareIdx / 8 // 0 bottom .. 7 top (visual)
                    val x = file * cell + cell / 2f
                    val y = (7 - rank) * cell + cell / 2f
                    return Offset(x, y)
                }

                val start = centerOf(arrowFrom)
                val end = centerOf(arrowTo)

                // Shorten so the arrowhead doesn't cover the destination piece.
                val dx = end.x - start.x
                val dy = end.y - start.y
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val ux = dx / len
                val uy = dy / len

                val headLen = cell * 0.35f
                val tailEnd = Offset(end.x - ux * headLen, end.y - uy * headLen)

                val stroke = cell * 0.12f
                val color = Color(0xFF9E9E9E).copy(alpha = 0.75f)

                drawLine(
                    color = color,
                    start = start,
                    end = tailEnd,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // Arrowhead
                val perp = Offset(-uy, ux)
                val left = Offset(
                    end.x - ux * headLen + perp.x * (headLen * 0.45f),
                    end.y - uy * headLen + perp.y * (headLen * 0.45f)
                )
                val right = Offset(
                    end.x - ux * headLen - perp.x * (headLen * 0.45f),
                    end.y - uy * headLen - perp.y * (headLen * 0.45f)
                )

                val path = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(left.x, left.y)
                    lineTo(right.x, right.y)
                    close()
                }
                drawPath(path = path, color = color)
            }
        }
    }


    /**
     * Shared evaluation bar for all modules (Tactics, Opening, Endgame, Beat the Fish).
     *
     * @param scoreCp  Engine score in centipawns from White's point of view.
     *                 Positive = White better, Negative = Black better.
     *                 Pass `null` to show neutral / engine-off state.
     * @param modifier Size/position from the caller (e.g. `width(18.dp).fillMaxHeight()`).
     * @param whiteOnTop If true, White's side of the bar is at the top edge.
     */

}


private fun originalPieceDrawableRes(p: Piece): Int {
    return when (p.type) {
        PieceType.KING -> if (p.isWhite) R.drawable.cburnett_wk else R.drawable.cburnett_bk
        PieceType.QUEEN -> if (p.isWhite) R.drawable.cburnett_wq else R.drawable.cburnett_bq
        PieceType.ROOK -> if (p.isWhite) R.drawable.cburnett_wr else R.drawable.cburnett_br
        PieceType.BISHOP -> if (p.isWhite) R.drawable.cburnett_wb else R.drawable.cburnett_bb
        PieceType.KNIGHT -> if (p.isWhite) R.drawable.cburnett_wn else R.drawable.cburnett_bn
        PieceType.PAWN -> if (p.isWhite) R.drawable.cburnett_wp else R.drawable.cburnett_bp
    }
}

@Composable
fun DrawPiece(p: Piece, style: PieceStyle, pieceSetKey: String = "original") {
    val context = LocalContext.current
    val setKey = pieceSetKey.trim().lowercase().ifBlank { "original" }

    if (isOriginalPieceSet(setKey)) {
        val resId = remember(setKey, p.type, p.isWhite, context) {
            originalPieceDrawableId(context, p)
        }
        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
            return
        }
    }

    val fileName = pieceAssetFileName(p)

    // Use Coil's real SVG decoder, same approach as Chess Openings Coach.
    // Do not hand-render SVGs here: Dubrovny/California use gradients, xlink:href,
    // opacity, transforms, and inherited styles that are easy to misdraw manually.
    val request = remember(setKey, fileName, context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/pieces/$setKey/$fileName")
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .allowHardware(false)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(4.dp),
        contentScale = ContentScale.Fit,
        loading = {},
        error = {
            Text(
                text = p.glyph,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    )
}

private fun pieceAssetFileName(p: Piece): String {
    val side = if (p.isWhite) "w" else "b"
    val kind = when (p.type) {
        PieceType.KING -> "K"
        PieceType.QUEEN -> "Q"
        PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"
        PieceType.KNIGHT -> "N"
        PieceType.PAWN -> "P"
    }
    return "$side$kind.svg"
}

fun loadPieceAssetBitmap(context: android.content.Context, pieceSetKey: String, piece: Piece): android.graphics.Bitmap? {
    val setKey = pieceSetKey.trim().lowercase().ifBlank { "original" }

    if (isOriginalPieceSet(setKey)) {
        val resId = originalPieceDrawableId(context, piece)
        if (resId != 0) {
            return android.graphics.BitmapFactory.decodeResource(context.resources, resId)
        }
    }

    val folder = "pieces/$setKey"
    val color = if (piece.isWhite) "w" else "b"
    val colorOther = if (piece.isWhite) "l" else "d"   // light/dark naming, used by some SVG packs
    val colorLong = if (piece.isWhite) "white" else "black"
    val code = when (piece.type) {
        PieceType.KING -> "k"
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        PieceType.PAWN -> "p"
    }
    val codeUpper = code.uppercase()
    val name = when (piece.type) {
        PieceType.KING -> "king"
        PieceType.QUEEN -> "queen"
        PieceType.ROOK -> "rook"
        PieceType.BISHOP -> "bishop"
        PieceType.KNIGHT -> "knight"
        PieceType.PAWN -> "pawn"
    }

    fun decodeRaster(path: String): android.graphics.Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    fun decodeSvg(path: String): android.graphics.Bitmap? = runCatching {
        val svgText = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        renderSimpleSvgToBitmap(svgText, sizePx = 512)
    }.getOrNull()

    fun decodeAny(path: String): android.graphics.Bitmap? {
        val lp = path.lowercase()
        return when {
            lp.endsWith(".svg") -> decodeSvg(path)
            lp.endsWith(".png") || lp.endsWith(".webp") || lp.endsWith(".jpg") || lp.endsWith(".jpeg") -> decodeRaster(path)
            else -> null
        }
    }

    val rasterExts = listOf("png", "webp", "jpg", "jpeg")
    val svgExts = listOf("svg")
    val allExts = rasterExts + svgExts

    // Direct candidates first. This covers the usual COC/Lichess shapes:
    // pieces/dubrovny/wK.svg, pieces/dubrovny/wk.svg, pieces/dubrovny/white/king.svg, etc.
    val baseNames = listOf(
        "$color$code",              // wk
        "$color$codeUpper",         // wK
        "${color}_$code",
        "$color-$code",
        "${color}_$codeUpper",
        "$color-$codeUpper",
        "$colorLong$name",          // whiteking
        "$colorLong${name.replaceFirstChar { it.uppercase() }}", // whiteKing
        "${colorLong}_$name",
        "${colorLong}-$name",
        "${name}_$colorLong",
        "${name}-$colorLong",
        "$name$colorLong",
        "$colorOther$code",         // lk / dk
        "$colorOther$codeUpper"      // lK / dK
    ).distinct()

    val likelySubfolders = listOf(
        "",
        color,
        colorLong,
        if (piece.isWhite) "white pieces" else "black pieces",
        "svg",
        "pieces",
        "images"
    )

    for (sub in likelySubfolders) {
        for (base in baseNames) {
            for (ext in allExts) {
                val path = if (sub.isBlank()) "$folder/$base.$ext" else "$folder/$sub/$base.$ext"
                val bmp = decodeAny(path)
                if (bmp != null) return bmp
            }
        }
    }

    // Recursive fallback. Some COC packs are nested one or two folders deep, so a one-level
    // assets.list(folder) misses the actual SVG files and the board falls back to glyphs.
    val files = listAssetFilesRecursive(context, folder)

    fun stripKnownExt(lf: String): String = lf
        .removeSuffix(".png")
        .removeSuffix(".webp")
        .removeSuffix(".jpg")
        .removeSuffix(".jpeg")
        .removeSuffix(".svg")

    fun normalizeFileName(s: String): String = s
        .lowercase()
        .substringAfterLast('/')
        .let { stripKnownExt(it) }
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")

    val exactNeedles = listOf(
        "$color$code", "$color$codeUpper",
        "$colorLong$name", "$name$colorLong",
        "$colorOther$code", "$colorOther$codeUpper"
    ).map { it.lowercase().replace("_", "").replace("-", "").replace(" ", "") }.distinct()

    val wordNeedles = listOf(
        colorLong to name,
        name to colorLong,
        color to code,
        colorOther to code
    )

    val match = files.firstOrNull { path ->
        val lp = path.lowercase()
        allExts.any { lp.endsWith(".$it") } && normalizeFileName(lp) in exactNeedles
    } ?: files.firstOrNull { path ->
        val lp = path.lowercase()
        if (!allExts.any { lp.endsWith(".$it") }) return@firstOrNull false
        val leaf = normalizeFileName(lp)
        wordNeedles.any { (a, b) -> leaf.contains(a.lowercase()) && leaf.contains(b.lowercase()) }
    } ?: files.firstOrNull { path ->
        // Last resort: if the pack uses only 12 files with cryptic names, still avoid a glyph
        // by matching common two-character codes anywhere in the file path.
        val lp = path.lowercase()
        allExts.any { lp.endsWith(".$it") } &&
                (lp.contains("/${color}${code}.") || lp.contains("/${color}${codeUpper.lowercase()}.") ||
                 lp.contains("_${color}${code}.") || lp.contains("-${color}${code}."))
    }

    return match?.let { decodeAny(it) }
}

private fun listAssetFilesRecursive(context: android.content.Context, root: String): List<String> {
    val out = ArrayList<String>()

    fun walk(path: String, depth: Int) {
        if (depth > 6) return
        val children = runCatching { context.assets.list(path)?.toList().orEmpty() }.getOrDefault(emptyList())
        if (children.isEmpty()) {
            // AssetManager.list(file) returns empty; only keep paths that look like image files.
            val lp = path.lowercase()
            if (lp.endsWith(".svg") || lp.endsWith(".png") || lp.endsWith(".webp") || lp.endsWith(".jpg") || lp.endsWith(".jpeg")) {
                out += path
            }
            return
        }
        for (child in children) {
            val childPath = if (path.isBlank()) child else "$path/$child"
            val lp = childPath.lowercase()
            if (lp.endsWith(".svg") || lp.endsWith(".png") || lp.endsWith(".webp") || lp.endsWith(".jpg") || lp.endsWith(".jpeg")) {
                out += childPath
            } else {
                walk(childPath, depth + 1)
            }
        }
    }

    walk(root, 0)
    return out
}

private fun renderSimpleSvgToBitmap(svgText: String, sizePx: Int = 512): android.graphics.Bitmap? {
    val viewBox = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(svgText)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.split(Regex("""[\s,]+"""))
        ?.mapNotNull { it.toFloatOrNull() }

    val minX = viewBox?.getOrNull(0) ?: 0f
    val minY = viewBox?.getOrNull(1) ?: 0f
    val vbW = (viewBox?.getOrNull(2) ?: 45f).takeIf { it > 0f } ?: 45f
    val vbH = (viewBox?.getOrNull(3) ?: 45f).takeIf { it > 0f } ?: 45f

    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val scale = kotlin.math.min(sizePx / vbW, sizePx / vbH)
    val dx = (sizePx - vbW * scale) / 2f
    val dy = (sizePx - vbH * scale) / 2f

    canvas.save()
    canvas.translate(dx, dy)
    canvas.scale(scale, scale)
    canvas.translate(-minX, -minY)

    data class SvgPaintSpec(
        val color: Int? = null,
        val gradientId: String? = null
    )

    data class SvgGradient(
        val id: String,
        val linear: Boolean,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val cx: Float,
        val cy: Float,
        val r: Float,
        val colors: IntArray,
        val stops: FloatArray?,
        val matrix: android.graphics.Matrix
    )

    data class SvgState(
        val fill: SvgPaintSpec?,
        val stroke: SvgPaintSpec?,
        val strokeWidth: Float,
        val fillEvenOdd: Boolean,
        val opacity: Float,
        val fillOpacity: Float,
        val strokeOpacity: Float,
        val cap: android.graphics.Paint.Cap,
        val join: android.graphics.Paint.Join,
        val matrix: android.graphics.Matrix
    )

    fun parseStyleDeclarations(style: String?): Map<String, String> {
        if (style.isNullOrBlank()) return emptyMap()
        return style.split(';')
            .mapNotNull { part ->
                val pieces = part.split(':', limit = 2)
                if (pieces.size == 2) pieces[0].trim().lowercase() to pieces[1].trim() else null
            }
            .toMap()
    }

    // Many COC / Lichess-style pieces store important fill/stroke rules in CSS classes,
    // e.g. <style>.white{fill:url(#piece_white)}</style><path class="white" .../>.
    // Android's tiny hand-rolled renderer must read those classes, otherwise gradients
    // collapse to black and some piece sets look like silhouettes.
    val classStyles: Map<String, Map<String, String>> = buildMap {
        val cssText = Regex("""<style\b[^>]*>(.*?)</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(svgText)
            .joinToString("\n") { it.groupValues[1] }
        val classRegex = Regex("""\.([A-Za-z_][A-Za-z0-9_-]*)\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        for (m in classRegex.findAll(cssText)) {
            put(m.groupValues[1].trim(), parseStyleDeclarations(m.groupValues[2]))
        }
    }

    fun directAttr(tag: String, attr: String): String? {
        Regex("""\b${Regex.escape(attr)}\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it.trim() }
        return null
    }

    fun attrFromTag(tag: String, attr: String): String? {
        val key = attr.lowercase()
        directAttr(tag, attr)?.let { return it }

        val inline = parseStyleDeclarations(directAttr(tag, "style"))
        inline[key]?.let { return it }

        val classes = directAttr(tag, "class")
            ?.split(Regex("""\s+"""))
            ?.filter { it.isNotBlank() }
            .orEmpty()

        // Later classes override earlier classes, similar to common CSS expectations.
        for (cls in classes.asReversed()) {
            classStyles[cls]?.get(key)?.let { return it }
        }
        return null
    }

    fun alphaColor(color: Int, opacity: Float): Int {
        val a = ((android.graphics.Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt()).coerceIn(0, 255)
        return android.graphics.Color.argb(
            a,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }

    fun parseColorValue(rawIn: String?): Int? {
        val raw = rawIn?.trim()?.lowercase() ?: return null
        if (raw.isBlank() || raw == "none" || raw == "transparent") return null
        if (raw.startsWith("url(")) return null
        if (raw.startsWith("#")) {
            return runCatching {
                val hex = raw.removePrefix("#")
                when (hex.length) {
                    3 -> {
                        val r = "${hex[0]}${hex[0]}".toInt(16)
                        val g = "${hex[1]}${hex[1]}".toInt(16)
                        val b = "${hex[2]}${hex[2]}".toInt(16)
                        android.graphics.Color.rgb(r, g, b)
                    }
                    4 -> {
                        val r = "${hex[0]}${hex[0]}".toInt(16)
                        val g = "${hex[1]}${hex[1]}".toInt(16)
                        val b = "${hex[2]}${hex[2]}".toInt(16)
                        val a = "${hex[3]}${hex[3]}".toInt(16)
                        android.graphics.Color.argb(a, r, g, b)
                    }
                    6 -> android.graphics.Color.rgb(
                        hex.substring(0, 2).toInt(16),
                        hex.substring(2, 4).toInt(16),
                        hex.substring(4, 6).toInt(16)
                    )
                    8 -> {
                        // SVG/CSS uses #RRGGBBAA more commonly than Android's #AARRGGBB.
                        val r = hex.substring(0, 2).toInt(16)
                        val g = hex.substring(2, 4).toInt(16)
                        val b = hex.substring(4, 6).toInt(16)
                        val a = hex.substring(6, 8).toInt(16)
                        android.graphics.Color.argb(a, r, g, b)
                    }
                    else -> null
                }
            }.getOrNull()
        }
        if (raw.startsWith("rgb")) {
            val nums = Regex("""[\d.]+""").findAll(raw).mapNotNull { it.value.toFloatOrNull() }.toList()
            if (nums.size >= 3) {
                val r = nums[0].toInt().coerceIn(0, 255)
                val g = nums[1].toInt().coerceIn(0, 255)
                val b = nums[2].toInt().coerceIn(0, 255)
                val a = if (nums.size >= 4) (nums[3] * 255f).toInt().coerceIn(0, 255) else 255
                return android.graphics.Color.argb(a, r, g, b)
            }
        }
        return when (raw) {
            "white" -> android.graphics.Color.WHITE
            "black" -> android.graphics.Color.BLACK
            "gray", "grey" -> android.graphics.Color.GRAY
            "lightgray", "lightgrey" -> android.graphics.Color.LTGRAY
            "darkgray", "darkgrey" -> android.graphics.Color.DKGRAY
            "red" -> android.graphics.Color.RED
            "blue" -> android.graphics.Color.BLUE
            "green" -> android.graphics.Color.GREEN
            "yellow" -> android.graphics.Color.YELLOW
            "brown" -> android.graphics.Color.rgb(150, 75, 0)
            else -> null
        }
    }

    fun parsePaint(rawIn: String?, inherited: SvgPaintSpec?): SvgPaintSpec? {
        val raw = rawIn?.trim() ?: return inherited
        val lower = raw.lowercase()
        if (lower == "none" || lower == "transparent") return null
        val url = Regex("""url\(\s*#([^\)\s]+)\s*\)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
        if (!url.isNullOrBlank()) return SvgPaintSpec(gradientId = url.trim())
        parseColorValue(raw)?.let { return SvgPaintSpec(color = it) }
        return inherited
    }

    fun parseFloatAttr(tag: String, attr: String): Float? =
        attrFromTag(tag, attr)
            ?.replace("px", "", ignoreCase = true)
            ?.trim()
            ?.toFloatOrNull()

    fun parseOpacityValue(raw: String?): Float? {
        val text = raw?.trim() ?: return null
        return if (text.endsWith("%")) {
            text.dropLast(1).toFloatOrNull()?.div(100f)
        } else text.toFloatOrNull()
    }

    fun parseCap(s: String?): android.graphics.Paint.Cap = when (s?.trim()?.lowercase()) {
        "butt" -> android.graphics.Paint.Cap.BUTT
        "square" -> android.graphics.Paint.Cap.SQUARE
        else -> android.graphics.Paint.Cap.ROUND
    }

    fun parseJoin(s: String?): android.graphics.Paint.Join = when (s?.trim()?.lowercase()) {
        "bevel" -> android.graphics.Paint.Join.BEVEL
        "miter" -> android.graphics.Paint.Join.MITER
        else -> android.graphics.Paint.Join.ROUND
    }

    fun parseMatrix(transform: String?): android.graphics.Matrix {
        val out = android.graphics.Matrix()
        val text = transform?.trim().orEmpty()
        if (text.isBlank()) return out

        val opRegex = Regex("""(matrix|translate|scale|rotate)\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
        for (m in opRegex.findAll(text)) {
            val op = m.groupValues[1].lowercase()
            val nums = m.groupValues[2]
                .split(Regex("""[\s,]+"""))
                .mapNotNull { it.toFloatOrNull() }
            val mm = android.graphics.Matrix()
            when (op) {
                "matrix" -> if (nums.size >= 6) {
                    mm.setValues(floatArrayOf(
                        nums[0], nums[2], nums[4],
                        nums[1], nums[3], nums[5],
                        0f, 0f, 1f
                    ))
                }
                "translate" -> {
                    val tx = nums.getOrNull(0) ?: 0f
                    val ty = nums.getOrNull(1) ?: 0f
                    mm.setTranslate(tx, ty)
                }
                "scale" -> {
                    val sx = nums.getOrNull(0) ?: 1f
                    val sy = nums.getOrNull(1) ?: sx
                    mm.setScale(sx, sy)
                }
                "rotate" -> {
                    val deg = nums.getOrNull(0) ?: 0f
                    if (nums.size >= 3) mm.setRotate(deg, nums[1], nums[2]) else mm.setRotate(deg)
                }
            }
            out.preConcat(mm)
        }
        return out
    }

    fun coord(raw: String?, default: Float, horizontal: Boolean): Float {
        val t = raw?.trim() ?: return default
        return if (t.endsWith("%")) {
            val pct = t.dropLast(1).toFloatOrNull()?.div(100f) ?: return default
            if (horizontal) minX + vbW * pct else minY + vbH * pct
        } else {
            t.replace("px", "", ignoreCase = true).toFloatOrNull() ?: default
        }
    }

    fun radius(raw: String?, default: Float): Float {
        val t = raw?.trim() ?: return default
        return if (t.endsWith("%")) {
            val pct = t.dropLast(1).toFloatOrNull()?.div(100f) ?: return default
            kotlin.math.min(vbW, vbH) * pct
        } else {
            t.replace("px", "", ignoreCase = true).toFloatOrNull() ?: default
        }
    }

    fun gradientAttr(tag: String, attr: String): String? = attrFromTag(tag, attr)

    data class RawGradient(
        val id: String,
        val kind: String,
        val tag: String,
        val href: String?,
        val colors: IntArray,
        val stops: FloatArray?
    )

    val rawGradients: Map<String, RawGradient> = buildMap {
        val gradientRegex = Regex("""<(linearGradient|radialGradient)\b([^>]*)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val stopRegex = Regex("""<stop\b([^>]*)/?>""", RegexOption.IGNORE_CASE)

        for (gm in gradientRegex.findAll(svgText)) {
            val kind = gm.groupValues[1].lowercase()
            val tag = "<${gm.groupValues[1]} ${gm.groupValues[2]}>"
            val body = gm.groupValues[3]
            val id = gradientAttr(tag, "id") ?: continue
            val href = (gradientAttr(tag, "xlink:href") ?: gradientAttr(tag, "href"))
                ?.trim()
                ?.removePrefix("#")

            val colors = ArrayList<Int>()
            val stops = ArrayList<Float>()
            for (sm in stopRegex.findAll(body)) {
                val stopTag = "<stop ${sm.groupValues[1]}>"
                val offRaw = attrFromTag(stopTag, "offset") ?: "0"
                val off = if (offRaw.trim().endsWith("%")) {
                    offRaw.trim().dropLast(1).toFloatOrNull()?.div(100f) ?: 0f
                } else offRaw.trim().toFloatOrNull() ?: 0f
                val color = parseColorValue(attrFromTag(stopTag, "stop-color")) ?: android.graphics.Color.BLACK
                val op = parseOpacityValue(attrFromTag(stopTag, "stop-opacity")) ?: 1f
                colors += alphaColor(color, op)
                stops += off.coerceIn(0f, 1f)
            }

            put(
                id,
                RawGradient(
                    id = id,
                    kind = kind,
                    tag = tag,
                    href = href,
                    colors = colors.toIntArray(),
                    stops = stops.toFloatArray().takeIf { it.isNotEmpty() }
                )
            )
        }
    }

    fun rawGradientAttr(id: String, attr: String, seen: Set<String> = emptySet()): String? {
        val raw = rawGradients[id] ?: return null
        gradientAttr(raw.tag, attr)?.let { return it }
        val ref = raw.href?.takeIf { it !in seen } ?: return null
        return rawGradientAttr(ref, attr, seen + id)
    }

    fun resolvedGradientStops(id: String, seen: Set<String> = emptySet()): Pair<IntArray, FloatArray?>? {
        val raw = rawGradients[id] ?: return null
        if (raw.colors.isNotEmpty()) {
            val colors = raw.colors.toMutableList()
            val stops = raw.stops?.toMutableList() ?: MutableList(colors.size) { idx ->
                if (colors.size <= 1) 0f else idx.toFloat() / (colors.size - 1).toFloat()
            }
            if (colors.size == 1) {
                colors += colors.first()
                stops += 1f
            }
            return colors.toIntArray() to stops.toFloatArray()
        }
        val ref = raw.href?.takeIf { it !in seen } ?: return null
        return resolvedGradientStops(ref, seen + id)
    }

    val gradients: Map<String, SvgGradient> = buildMap {
        for ((id, raw) in rawGradients) {
            val resolved = resolvedGradientStops(id) ?: continue
            val colors = resolved.first
            val stops = resolved.second

            put(
                id,
                SvgGradient(
                    id = id,
                    linear = raw.kind == "lineargradient",
                    x1 = coord(rawGradientAttr(id, "x1"), minX, horizontal = true),
                    y1 = coord(rawGradientAttr(id, "y1"), minY, horizontal = false),
                    x2 = coord(rawGradientAttr(id, "x2"), minX + vbW, horizontal = true),
                    y2 = coord(rawGradientAttr(id, "y2"), minY, horizontal = false),
                    cx = coord(rawGradientAttr(id, "cx"), minX + vbW / 2f, horizontal = true),
                    cy = coord(rawGradientAttr(id, "cy"), minY + vbH / 2f, horizontal = false),
                    r = radius(rawGradientAttr(id, "r"), kotlin.math.min(vbW, vbH) / 2f),
                    colors = colors,
                    stops = stops,
                    matrix = parseMatrix(rawGradientAttr(id, "gradientTransform"))
                )
            )
        }
    }

    fun shaderFor(spec: SvgPaintSpec?): android.graphics.Shader? {
        val id = spec?.gradientId ?: return null
        val g = gradients[id] ?: return null
        val shader: android.graphics.Shader = if (g.linear) {
            android.graphics.LinearGradient(g.x1, g.y1, g.x2, g.y2, g.colors, g.stops, android.graphics.Shader.TileMode.CLAMP)
        } else {
            android.graphics.RadialGradient(g.cx, g.cy, g.r.coerceAtLeast(0.01f), g.colors, g.stops, android.graphics.Shader.TileMode.CLAMP)
        }
        shader.setLocalMatrix(g.matrix)
        return shader
    }

    fun mergedState(parent: SvgState, tag: String): SvgState {
        val tagMatrix = parseMatrix(attrFromTag(tag, "transform"))
        val matrix = android.graphics.Matrix(parent.matrix).apply { preConcat(tagMatrix) }

        val fill = parsePaint(attrFromTag(tag, "fill"), parent.fill)
        val stroke = parsePaint(attrFromTag(tag, "stroke"), parent.stroke)

        val fillRuleText = attrFromTag(tag, "fill-rule") ?: attrFromTag(tag, "clip-rule")
        val fillEvenOdd = when {
            fillRuleText?.contains("evenodd", ignoreCase = true) == true -> true
            fillRuleText?.contains("nonzero", ignoreCase = true) == true -> false
            tag.contains("evenodd", ignoreCase = true) -> true
            else -> parent.fillEvenOdd
        }

        return SvgState(
            fill = fill,
            stroke = stroke,
            strokeWidth = parseFloatAttr(tag, "stroke-width") ?: parent.strokeWidth,
            fillEvenOdd = fillEvenOdd,
            opacity = (parseOpacityValue(attrFromTag(tag, "opacity")) ?: parent.opacity).coerceIn(0f, 1f),
            fillOpacity = (parseOpacityValue(attrFromTag(tag, "fill-opacity")) ?: parent.fillOpacity).coerceIn(0f, 1f),
            strokeOpacity = (parseOpacityValue(attrFromTag(tag, "stroke-opacity")) ?: parent.strokeOpacity).coerceIn(0f, 1f),
            cap = if (attrFromTag(tag, "stroke-linecap") != null) parseCap(attrFromTag(tag, "stroke-linecap")) else parent.cap,
            join = if (attrFromTag(tag, "stroke-linejoin") != null) parseJoin(attrFromTag(tag, "stroke-linejoin")) else parent.join,
            matrix = matrix
        )
    }

    val rootState = SvgState(
        fill = SvgPaintSpec(color = android.graphics.Color.BLACK),
        stroke = null,
        strokeWidth = 1f,
        fillEvenOdd = false,
        opacity = 1f,
        fillOpacity = 1f,
        strokeOpacity = 1f,
        cap = android.graphics.Paint.Cap.ROUND,
        join = android.graphics.Paint.Join.ROUND,
        matrix = android.graphics.Matrix()
    )

    val stack = ArrayDeque<SvgState>()
    stack.addLast(rootState)

    val tagRegex = Regex("""</?\s*(g|path|ellipse|circle|rect|svg)\b[^>]*>""", RegexOption.IGNORE_CASE)
    val dRegex = Regex("""\bd\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    for (m in tagRegex.findAll(svgText)) {
        val tag = m.value
        val lower = tag.lowercase()
        val isClosing = lower.startsWith("</")
        val isSelfClosing = lower.endsWith("/>")
        val name = m.groupValues[1].lowercase()

        if (isClosing) {
            if ((name == "g" || name == "svg") && stack.size > 1) stack.removeLast()
            continue
        }

        val parent = stack.lastOrNull() ?: rootState
        val state = mergedState(parent, tag)

        if (name == "g" || name == "svg") {
            if (!isSelfClosing) stack.addLast(state)
            continue
        }

        if (name == "path" || name == "ellipse" || name == "circle" || name == "rect") {
            val path: android.graphics.Path = when (name) {
                "path" -> {
                    val d = dRegex.find(tag)?.groupValues?.getOrNull(1) ?: continue
                    runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: continue
                }
                "ellipse" -> {
                    val cx = coord(attrFromTag(tag, "cx"), 0f, horizontal = true)
                    val cy = coord(attrFromTag(tag, "cy"), 0f, horizontal = false)
                    val rx = radius(attrFromTag(tag, "rx"), 0f)
                    val ry = radius(attrFromTag(tag, "ry"), rx)
                    android.graphics.Path().apply {
                        addOval(android.graphics.RectF(cx - rx, cy - ry, cx + rx, cy + ry), android.graphics.Path.Direction.CW)
                    }
                }
                "circle" -> {
                    val cx = coord(attrFromTag(tag, "cx"), 0f, horizontal = true)
                    val cy = coord(attrFromTag(tag, "cy"), 0f, horizontal = false)
                    val r = radius(attrFromTag(tag, "r"), 0f)
                    android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) }
                }
                else -> {
                    val x = coord(attrFromTag(tag, "x"), 0f, horizontal = true)
                    val y = coord(attrFromTag(tag, "y"), 0f, horizontal = false)
                    val w = coord(attrFromTag(tag, "width"), 0f, horizontal = true)
                    val h = coord(attrFromTag(tag, "height"), 0f, horizontal = false)
                    android.graphics.Path().apply {
                        addRect(android.graphics.RectF(x, y, x + w, y + h), android.graphics.Path.Direction.CW)
                    }
                }
            }

            path.transform(state.matrix)
            if (state.fillEvenOdd) path.fillType = android.graphics.Path.FillType.EVEN_ODD

            if (state.fill != null) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL
                    shader = shaderFor(state.fill)
                    color = alphaColor(state.fill.color ?: android.graphics.Color.BLACK, state.opacity * state.fillOpacity)
                }
                canvas.drawPath(path, paint)
            }

            if (state.stroke != null && state.strokeWidth > 0f) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    shader = shaderFor(state.stroke)
                    color = alphaColor(state.stroke.color ?: android.graphics.Color.BLACK, state.opacity * state.strokeOpacity)
                    strokeWidth = state.strokeWidth
                    strokeCap = state.cap
                    strokeJoin = state.join
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    canvas.restore()
    return bitmap
}

private fun svgAttr(tag: String, attr: String): String? {
    Regex("""\b${Regex.escape(attr)}\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(tag)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { return it.trim() }

    Regex("""style\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(tag)
        ?.groupValues
        ?.getOrNull(1)
        ?.split(';')
        ?.mapNotNull { part ->
            val pieces = part.split(':', limit = 2)
            if (pieces.size == 2) pieces[0].trim() to pieces[1].trim() else null
        }
        ?.firstOrNull { it.first.equals(attr, ignoreCase = true) }
        ?.second
        ?.let { return it }

    return null
}

private fun svgNumberFromTag(tag: String, attr: String): Float? =
    svgAttr(tag, attr)?.replace("px", "", ignoreCase = true)?.toFloatOrNull()

private fun svgColorFromTag(tag: String, attr: String): Int? {
    val raw = svgAttr(tag, attr)?.trim()?.lowercase() ?: return null
    if (raw == "none" || raw == "transparent") return null
    if (raw.startsWith("url(")) return null
    if (raw.startsWith("#")) {
        return runCatching {
            val hex = raw.removePrefix("#")
            when (hex.length) {
                3 -> {
                    val r = "${hex[0]}${hex[0]}".toInt(16)
                    val g = "${hex[1]}${hex[1]}".toInt(16)
                    val b = "${hex[2]}${hex[2]}".toInt(16)
                    android.graphics.Color.rgb(r, g, b)
                }
                6 -> android.graphics.Color.rgb(
                    hex.substring(0, 2).toInt(16),
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16)
                )
                8 -> {
                    val r = hex.substring(0, 2).toInt(16)
                    val g = hex.substring(2, 4).toInt(16)
                    val b = hex.substring(4, 6).toInt(16)
                    val a = hex.substring(6, 8).toInt(16)
                    android.graphics.Color.argb(a, r, g, b)
                }
                else -> null
            }
        }.getOrNull()
    }
    return when (raw) {
        "white" -> android.graphics.Color.WHITE
        "black" -> android.graphics.Color.BLACK
        "gray", "grey" -> android.graphics.Color.GRAY
        "lightgray", "lightgrey" -> android.graphics.Color.LTGRAY
        "darkgray", "darkgrey" -> android.graphics.Color.DKGRAY
        "red" -> android.graphics.Color.RED
        "blue" -> android.graphics.Color.BLUE
        "green" -> android.graphics.Color.GREEN
        else -> null
    }
}


@Composable
fun EvalBar(
    scoreCp: Int?,
    modifier: Modifier = Modifier,
    whiteOnTop: Boolean = true
) {

    // scoreCp: centipawns from White POV (positive = White better)
    val cp = scoreCp ?: 0

    // Saturate at ±4.0 pawns (±400 cp)
    val clamped = cp.coerceIn(-400, 400)

    // +200cp => 0.75, +400cp => 1.0, -200cp => 0.25, -400cp => 0.0
    // Avoid exactly 0f or 1f because weight(0f) can crash.
    val eps = 0.02f
    val whitePortion = (0.5f + (clamped / 800f)).coerceIn(eps, 1f - eps)

    val animatedWhite by animateFloatAsState(
        targetValue = whitePortion,
        animationSpec = tween(durationMillis = 250),
        label = "evalBar"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF202124))
    ) {
        val horizontal = maxWidth > maxHeight

        if (horizontal) {
            // Horizontal eval bar: White is on the left, Black is on the right.
            // Positive White POV eval grows the white segment toward the right.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().weight(animatedWhite).background(Color.White))
                Box(Modifier.fillMaxHeight().weight(1f - animatedWhite).background(Color.Black))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                val topWeight = if (whiteOnTop) animatedWhite else (1f - animatedWhite)
                val bottomWeight = 1f - topWeight

                val topColor = if (whiteOnTop) Color.White else Color.Black
                val bottomColor = if (whiteOnTop) Color.Black else Color.White

                Box(Modifier.fillMaxWidth().weight(topWeight).background(topColor))
                Box(Modifier.fillMaxWidth().weight(bottomWeight).background(bottomColor))
            }
        }
    }
}
