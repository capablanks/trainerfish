package com.tonorbe.trainerfish.pgn

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator

// Standard initial chess position (keep in sync with PgnReplay.kt)
private const val START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * PGN token semantics used by BOTH:
 *  - parsing (tokens -> tree)
 *  - emitting/writing (tree -> tokens -> text)
 *
 * Goal: one canonical token model so we don't "lose" comments/variations/nags.
 */
sealed interface PgnToken {
    /** One SAN move token (already stripped of "3." or "10..." prefixes). */
    data class San(val san: String) : PgnToken

    /** Null move / pass token used in some PGN books ("--"). */
    data object NullMove : PgnToken

    /** Numeric annotation glyph ($1, $2, ...). */
    data class Nag(val nag: String) : PgnToken

    /** Symbolic NAGs like ! ? !! ?? !? ?! */
    data class NagSymbol(val sym: String) : PgnToken

    /** "{ ... }" comment, body is raw (not including braces). */
    data class BraceComment(val body: String) : PgnToken

    /** "; ...\n" comment, body is raw (not including semicolon). */
    data class LineComment(val body: String) : PgnToken

    /** Start of a Recursive Annotation Variation "(" */
    data object RavStart : PgnToken

    /** End of a Recursive Annotation Variation ")" */
    data object RavEnd : PgnToken

    /** Game termination marker (1-0, 0-1, 1/2-1/2, *). */
    data class Result(val value: String) : PgnToken

    /** Move number tokens like "12." or "12..." (kept for round-tripping if desired). */
    data class MoveNumber(val raw: String) : PgnToken

    /** Any other token we didn't interpret. */
    data class Other(val raw: String) : PgnToken
}

/**
 * Tokenize *movetext* (not headers).
 * - Preserves comments and parentheses structure.
 * - Splits combined tokens like "3.Nf3" or "10...e5" into MoveNumber + San.
 */
fun pgnTokenizeMovetext(movetextRaw: String): List<PgnToken> {
    // Normalize common variants seen in PGN books (especially Chessable exports).
    // - Unicode ellipsis (... U+2026) -> "..."
    val s = movetextRaw.replace("\r\n", "\n").replace("\u2026", "...")
    val out = ArrayList<PgnToken>(s.length / 2)

    var i = 0
    fun peek(): Char? = if (i < s.length) s[i] else null
    fun take(): Char? = if (i < s.length) s[i++] else null

    fun skipWs() {
        while (true) {
            val c = peek() ?: break
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++ else break
        }
    }

    fun readUntil(endChar: Char): String {
        val start = i
        while (true) {
            val c = peek() ?: break
            if (c == endChar) break
            i++
        }
        return s.substring(start, i)
    }

    fun readLine(): String {
        val start = i
        while (true) {
            val c = peek() ?: break
            if (c == '\n') break
            i++
        }
        return s.substring(start, i)
    }

    fun readToken(): String {
        val start = i
        while (true) {
            val c = peek() ?: break
            if (c.isWhitespace() || c == '(' || c == ')' || c == '{' || c == '}' || c == ';') break
            i++
        }
        return s.substring(start, i)
    }

    fun isMoveNumber(tok: String): Boolean =
        tok.matches(Regex("""^\d+\.$""")) || tok.matches(Regex("""^\d+\.\.\.$"""))

    fun isResult(tok: String): Boolean =
        tok == "1-0" || tok == "0-1" || tok == "1/2-1/2" || tok == "*"


    // Split a symbolic NAG suffix from a SAN token, e.g. "e4!" -> ("e4", "!"), "Nf3!?" -> ("Nf3", "!?").
    // Returns: (san, nagSymOrNull)
    fun peelNagSuffix(tok0: String): Pair<String, String?> {
        val tok = tok0.trim()
        if (tok.isEmpty()) return tok to null
        // Prefer 2-char symbolic NAGs.
        val two = listOf("!!", "??", "!?", "?!")
        for (suf in two) {
            if (tok.length > suf.length && tok.endsWith(suf)) {
                return tok.dropLast(suf.length) to suf
            }
        }
        val one = listOf("!", "?")
        for (suf in one) {
            if (tok.length > 1 && tok.endsWith(suf)) {
                return tok.dropLast(1) to suf
            }
        }
        return tok to null
    }

    while (i < s.length) {
        skipWs()
        val c = peek() ?: break
        when (c) {
            '{' -> {
                take()
                val body = readUntil('}')
                if (peek() == '}') take()
                out.add(PgnToken.BraceComment(body))
            }
            ';' -> {
                take()
                val body = readLine()
                out.add(PgnToken.LineComment(body))
            }
            '(' -> { take(); out.add(PgnToken.RavStart) }
            ')' -> { take(); out.add(PgnToken.RavEnd) }
            '$' -> {
                take()
                val num = readToken().takeWhile { it.isDigit() }
                if (num.isNotEmpty()) out.add(PgnToken.Nag("$$num"))
            }
            else -> {
                var tok = readToken().trim()
                if (tok.isBlank()) {
                    i++
                    continue
                }

                // Chessable style: "3.Nf3", "10...e5", "12...Bb4+".
                // We keep the move-number token (for round-tripping) but also produce a SAN token.
                run {
                    val m = Regex("""^(\d+)(\.\.\.|\.{1,2})(.+)$""").matchEntire(tok)
                    if (m != null) {
                        val moveNo = m.groupValues[1] + m.groupValues[2]
                        val rest = m.groupValues[3].trim()
                        out.add(PgnToken.MoveNumber(moveNo))
                        tok = rest
                    }
                }

                if (tok.isBlank()) continue

                // Null move used by some PGN books ("1. -- *"). Treat as a first-class token.
                if (tok == "--") {
                    out.add(PgnToken.NullMove)
                    continue
                }

                // If a symbolic NAG is attached to a move token (e.g., "e4!", "Nf3!?"), peel it off.
                val (tokCore, nagSym) = peelNagSuffix(tok)
                tok = tokCore.trim()
                if (tok.isBlank()) {
                    // Pure NAG-like token; keep it as a NagSymbol if we can.
                    if (!nagSym.isNullOrBlank()) out.add(PgnToken.NagSymbol(nagSym))
                    continue
                }

                when {
                    isMoveNumber(tok) -> out.add(PgnToken.MoveNumber(tok))
                    isResult(tok) -> out.add(PgnToken.Result(tok))
                    tok in listOf("!", "?", "!!", "??", "!?", "?!") -> out.add(PgnToken.NagSymbol(tok))
                    else -> {
                        out.add(PgnToken.San(tok))
                        if (!nagSym.isNullOrBlank()) out.add(PgnToken.NagSymbol(nagSym))
                    }
                }
            }
        }
    }

    return out
}

/**
 * Build a [PgnTree] from a full PGN chunk (headers + movetext).
 *
 * NOTE: This does not rely on chesslib's PGN parsing for variations.
 * We parse variations/comments ourselves so UI and writer can share identical semantics.
 */
fun parsePgnTreeFromChunkTokens(chunkRaw: String): PgnTree {
    val chunk = chunkRaw.replace("\r\n", "\n")
    val parts = chunk.split("\n\n", limit = 2)
    val movetextPart = if (parts.size == 2) parts[1] else chunk
    val startFen = runCatching { // function is in PgnReplay.kt (same package)
        val fenTag = Regex("""(?im)^\s*\[FEN\s+\"([^\"]+)\"\]""")
        fenTag.find(chunk)?.groupValues?.get(1)?.trim()
    }.getOrNull()

    val tokens = pgnTokenizeMovetext(movetextPart)
    return buildTreeFromTokens(tokens, startFen)
}

// ---------------------------------------------------------------------------
// tokens -> tree
// ---------------------------------------------------------------------------

private data class VarFrame(
    val resumeNodeId: Int,
    val resumeFen: String,
    val baseNodeId: Int,
    val baseFen: String
)

private fun buildTreeFromTokens(tokens: List<PgnToken>, startFen: String?): PgnTree {
    val nodes = mutableListOf<PgnTreeNode>()
    nodes.add(PgnTreeNode(id = 0, parentId = null, san = "", uci = null))

    val board = Board()
    runCatching {
        if (!startFen.isNullOrBlank()) board.loadFromFen(startFen.trim())
        else board.loadFromFen(START_FEN)
    }.onFailure { board.loadFromFen(START_FEN) }

    fun curFen(): String = runCatching { board.fen }.getOrElse { START_FEN }

    // Track the exact FEN after each node id (root included)
    val fenAfterByNodeId = HashMap<Int, String>(tokens.size + 8)
    fenAfterByNodeId[0] = curFen()

    val varStack = ArrayDeque<VarFrame>()
    var cursorNodeId = 0
    var lastMoveNodeId: Int? = null
    var atVariationStart = false

    var pendingPreComment: String? = null
    var introComment: String? = null

    fun addIntroOrPreComment(textRaw: String) {
        val t = textRaw.trim()
        if (t.isEmpty()) return
        if (lastMoveNodeId == null && varStack.isEmpty()) {
            // Before any moves in the mainline.
            introComment = if (introComment.isNullOrBlank()) t else (introComment + "\n" + t)
        } else {
            pendingPreComment = if (pendingPreComment.isNullOrBlank()) t else (pendingPreComment + "\n" + t)
        }
    }

    fun addPostComment(textRaw: String) {
        val t = textRaw.trim()
        if (t.isEmpty()) return
        val lastId = lastMoveNodeId
        if (lastId == null) {
            addIntroOrPreComment(t)
            return
        }
        val n = nodes[lastId]
        n.postComment = if (n.postComment.isNullOrBlank()) t else (n.postComment + "\n" + t)
    }

    fun attachNag(nag: String) {
        val lastId = lastMoveNodeId ?: return
        nodes[lastId].nags.add(nag)
    }

    fun restoreToFen(fen: String) {
        runCatching { board.loadFromFen(fen) }.onFailure { board.loadFromFen(START_FEN) }
    }

    fun addMoveNode(sanRaw: String) {
        val san = sanRaw.trim()
        if (san.isEmpty()) return

        // Safety: never treat move numbers / stray dots as SAN moves.
        if (san.matches(Regex("""^\d+\.$""")) || san.matches(Regex("""^\d+\.\.\.$""")) || san == "." || san == ".." || san == "...") return

        val parentId = if (atVariationStart) varStack.last().baseNodeId else cursorNodeId
        val newId = nodes.size

        val node = PgnTreeNode(
            id = newId,
            parentId = parentId,
            san = san,
            preComment = pendingPreComment?.takeIf { it.isNotBlank() }
        )
        pendingPreComment = null

        // Resolve SAN -> legal move -> UCI (best effort)
        val mv = sanToLegalMove(board, san)
        if (mv != null) {
            node.uci = moveToUci(mv)
            runCatching { board.doMove(mv) }
        }

        nodes.add(node)

        if (atVariationStart) {
            nodes[parentId].variations.add(newId)
            atVariationStart = false
        } else {
            nodes[cursorNodeId].nextId = newId
        }

        cursorNodeId = newId
        lastMoveNodeId = newId
        fenAfterByNodeId[newId] = curFen()
    }

    fun flipFenSide(fen: String): String {
        // FEN: "pieces side castling ep halfmove fullmove"
        val parts = fen.trim().split(Regex("\\s+"))
        if (parts.size < 6) return fen
        val side = parts[1]
        val halfmove = parts[4].toIntOrNull() ?: 0
        val fullmove = parts[5].toIntOrNull() ?: 1
        val nextSide = if (side == "w") "b" else "w"
        val nextFullmove = if (side == "b") (fullmove + 1) else fullmove
        val nextHalfmove = halfmove + 1
        val rebuilt = parts.toMutableList()
        rebuilt[1] = nextSide
        rebuilt[4] = nextHalfmove.toString()
        rebuilt[5] = nextFullmove.toString()
        return rebuilt.joinToString(" ")
    }

    fun addNullMoveNode() {
        val parentId = if (atVariationStart) varStack.last().baseNodeId else cursorNodeId
        val newId = nodes.size

        val node = PgnTreeNode(
            id = newId,
            parentId = parentId,
            san = "--",
            uci = null,
            preComment = pendingPreComment?.takeIf { it.isNotBlank() }
        )
        pendingPreComment = null

        nodes.add(node)

        if (atVariationStart) {
            nodes[parentId].variations.add(newId)
            atVariationStart = false
        } else {
            nodes[cursorNodeId].nextId = newId
        }

        cursorNodeId = newId
        lastMoveNodeId = newId

        // Semantic effect: flip side-to-move while keeping pieces the same.
        val flipped = flipFenSide(curFen())
        restoreToFen(flipped)
        fenAfterByNodeId[newId] = curFen()
    }


    for (t in tokens) {
        when (t) {
            is PgnToken.BraceComment -> {
                // Brace comments typically attach to the PREVIOUS move if one exists.
                addPostComment(t.body)
            }
            is PgnToken.LineComment -> {
                addPostComment(t.body)
            }
            is PgnToken.Nag -> attachNag(t.nag)
            is PgnToken.NagSymbol -> attachNag(t.sym)
            is PgnToken.RavStart -> {
                // Variation is an alternative to the *last* move, branching from the position BEFORE it.
                val resumeNode = cursorNodeId
                val resumeFen = fenAfterByNodeId[resumeNode] ?: curFen()
                val baseNode = nodes.getOrNull(resumeNode)?.parentId ?: resumeNode
                val baseFen = fenAfterByNodeId[baseNode] ?: resumeFen

                varStack.addLast(
                    VarFrame(
                        resumeNodeId = resumeNode,
                        resumeFen = resumeFen,
                        baseNodeId = baseNode,
                        baseFen = baseFen
                    )
                )

                // Jump board/cursor back to base position.
                cursorNodeId = baseNode
                restoreToFen(baseFen)
                atVariationStart = true
                lastMoveNodeId = null // inside variation, comments before first move are "pre" of that move
            }
            is PgnToken.RavEnd -> {
                val f = varStack.removeLastOrNull()
                if (f != null) {
                    cursorNodeId = f.resumeNodeId
                    restoreToFen(f.resumeFen)
                    lastMoveNodeId = f.resumeNodeId
                }
                atVariationStart = false
            }
            is PgnToken.San -> addMoveNode(t.san)
            is PgnToken.NullMove -> addNullMoveNode()
            is PgnToken.Result -> { /* ignore for tree */ }
            is PgnToken.MoveNumber -> { /* ignore for tree, but kept for round-tripping elsewhere */ }
            is PgnToken.Other -> { /* ignore */ }
        }
    }

    return PgnTree(
        startFen = startFen,
        introComment = introComment?.trim()?.takeIf { it.isNotBlank() },
        nodes = nodes
    )
}

// ---------------------------------------------------------------------------
// SAN helpers (self-contained, so PgnParser.kt doesn't depend on UI code)
// ---------------------------------------------------------------------------

private fun squareFromAlgebra(algebra: String): Square = Square.valueOf(algebra.uppercase())

private fun pieceTypeFromLetter(ch: Char): PieceType = when (ch) {
    'K' -> PieceType.KING
    'Q' -> PieceType.QUEEN
    'R' -> PieceType.ROOK
    'B' -> PieceType.BISHOP
    'N' -> PieceType.KNIGHT
    else -> PieceType.PAWN
}

private fun normalizeSan(s: String): String = s
    .replace("+", "")
    .replace("#", "")
    .replace("e.p.", "")
    .trim()

private fun moveToUci(m: Move): String {
    val from = m.from.toString().lowercase()
    val to = m.to.toString().lowercase()
    val promo = m.promotion
    if (promo != null && promo != Piece.NONE) {
        val ch = when (promo.pieceType) {
            PieceType.QUEEN -> 'q'
            PieceType.ROOK -> 'r'
            PieceType.BISHOP -> 'b'
            PieceType.KNIGHT -> 'n'
            else -> 'q'
        }
        return "$from$to$ch"
    }
    return "$from$to"
}

private fun sanToLegalMove(board: Board, sanRaw: String): Move? {
    var san = normalizeSan(sanRaw)

    // Castling
    if (san.startsWith("O-O")) {
        val longCastle = san.startsWith("O-O-O")
        val white = (board.sideToMove == Side.WHITE)
        val toSq = when {
            white && longCastle -> Square.C1
            white && !longCastle -> Square.G1
            !white && longCastle -> Square.C8
            else -> Square.G8
        }
        return MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == PieceType.KING && mv.to == toSq
        }
    }

    val rePromo = Regex("^([a-h])x?([a-h][18])=([QRBN])$")
    val rePawnCap = Regex("^([a-h])x([a-h][1-8])$")
    val rePawnPush = Regex("^([a-h][1-8])$")
    val rePiece = Regex("^([KQRBN])([a-h1-8]?)(x?)([a-h][1-8])$")

    rePromo.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebra(m.groupValues[2])
        val promoType = pieceTypeFromLetter(m.groupValues[3][0])
        return MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == PieceType.PAWN &&
                    mv.to == to &&
                    mv.promotion?.pieceType == promoType &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    rePawnCap.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebra(m.groupValues[2])
        return MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == PieceType.PAWN &&
                    mv.to == to &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    rePawnPush.matchEntire(san)?.let { m ->
        val to = squareFromAlgebra(m.groupValues[1])
        return MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == PieceType.PAWN && mv.to == to
        }
    }

    rePiece.matchEntire(san)?.let { m ->
        val pt = pieceTypeFromLetter(m.groupValues[1][0])
        val disamb = m.groupValues[2]
        val to = squareFromAlgebra(m.groupValues[4])
        return MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            if (p.pieceType != pt) return@firstOrNull false
            if (mv.to != to) return@firstOrNull false
            if (disamb.isNotEmpty()) {
                val ch = disamb[0]
                val fromStr = mv.from.toString().lowercase()
                if (ch in 'a'..'h' && fromStr[0] != ch) return@firstOrNull false
                if (ch in '1'..'8' && fromStr[1] != ch) return@firstOrNull false
            }
            true
        }
    }

    return null
}
