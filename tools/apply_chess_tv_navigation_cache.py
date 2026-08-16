from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "app/src/main/java/com/tonorbe/trainerfish/LichessTvScreen.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label}: anchor not found")
    return text.replace(old, new, 1)


text = TV.read_text(encoding="utf-8")

text = replace_once(
    text,
    '''    var analysisStartFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    var analysisFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    var analysisUciMoves by remember { mutableStateOf<List<String>>(emptyList()) }
''',
    '''    var analysisStartFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    var analysisFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    // Cached FEN for ply 0..N. Chess TV navigation used to reconstruct every
    // target position from the initial FEN on every tap, so move 60 replayed
    // roughly sixty legal-move generations just to move the cursor once.
    var analysisFenByPly by remember { mutableStateOf<List<String>>(emptyList()) }
    var analysisUciMoves by remember { mutableStateOf<List<String>>(emptyList()) }
''',
    "analysis FEN cache state"
)

text = replace_once(
    text,
    '''    fun positionAt(startFen: String, moves: List<String>, ply: Int): String? {
        val board = runCatching { Board().apply { loadFromFen(startFen) } }.getOrNull() ?: return null
        for (moveUci in moves.take(ply.coerceIn(0, moves.size))) {
            val move = bfUciToMoveOnBoard(board, moveUci) ?: return null
            board.doMove(move)
        }
        return board.fen
    }
''',
    '''    fun positionsAlongLine(startFen: String, moves: List<String>): List<String> {
        val board = runCatching { Board().apply { loadFromFen(startFen) } }.getOrNull()
            ?: return emptyList()
        val positions = ArrayList<String>(moves.size + 1)
        positions += board.fen
        for (moveUci in moves) {
            val move = bfUciToMoveOnBoard(board, moveUci) ?: break
            if (!board.doMove(move)) break
            positions += board.fen
        }
        return positions
    }
''',
    "position cache builder"
)

text = replace_once(
    text,
    '''        analysisStartFen = tv.startFen
        analysisUciMoves = tv.uciMoves
        analysisSanMoves = tv.sanMoves
        analysisPly = targetPly.coerceIn(0, tv.uciMoves.size)
        analysisFen = positionAt(tv.startFen, tv.uciMoves, analysisPly)
            ?: if (analysisPly == tv.uciMoves.size) tv.fen else tv.startFen
''',
    '''        analysisStartFen = tv.startFen
        analysisUciMoves = tv.uciMoves
        analysisSanMoves = tv.sanMoves
        val positions = positionsAlongLine(tv.startFen, tv.uciMoves)
        analysisFenByPly = positions
        analysisPly = targetPly.coerceIn(0, tv.uciMoves.size)
        analysisFen = positions.getOrNull(analysisPly)
            ?: if (analysisPly == tv.uciMoves.size) tv.fen else tv.startFen
''',
    "enter analysis cached position"
)

text = replace_once(
    text,
    '''        val safePly = targetPly.coerceIn(0, analysisUciMoves.size)
        val fen = positionAt(analysisStartFen, analysisUciMoves, safePly) ?: return
        analysisPly = safePly
        analysisFen = fen
        selectedSquare = null
''',
    '''        val safePly = targetPly.coerceIn(0, analysisUciMoves.size)
        val positions = analysisFenByPly.takeIf {
            it.size == analysisUciMoves.size + 1
        } ?: positionsAlongLine(analysisStartFen, analysisUciMoves).also {
            analysisFenByPly = it
        }
        val fen = positions.getOrNull(safePly) ?: return
        analysisPly = safePly
        analysisFen = fen
        selectedSquare = null
''',
    "O(1) analysis navigation"
)

text = replace_once(
    text,
    '''        analysisUciMoves = analysisUciMoves.take(analysisPly) + uci
        analysisSanMoves = analysisSanMoves.take(analysisPly) + san
        analysisWasEdited = true
        analysisPly += 1
        analysisFen = board.fen
''',
    '''        analysisUciMoves = analysisUciMoves.take(analysisPly) + uci
        analysisSanMoves = analysisSanMoves.take(analysisPly) + san
        val cachedPrefix = analysisFenByPly.take(analysisPly + 1)
        analysisFenByPly = if (cachedPrefix.size == analysisPly + 1) {
            cachedPrefix + board.fen
        } else {
            positionsAlongLine(analysisStartFen, analysisUciMoves)
        }
        analysisWasEdited = true
        analysisPly += 1
        analysisFen = board.fen
''',
    "edited line cache update"
)

text = replace_once(
    text,
    '''            analysisStartFen = tv.startFen
            analysisUciMoves = tv.uciMoves
            analysisSanMoves = tv.sanMoves
            analysisPly = tv.uciMoves.size
            analysisFen = positionAt(tv.startFen, tv.uciMoves, tv.uciMoves.size) ?: analysisFen
''',
    '''            analysisStartFen = tv.startFen
            analysisUciMoves = tv.uciMoves
            analysisSanMoves = tv.sanMoves
            val positions = positionsAlongLine(tv.startFen, tv.uciMoves)
            analysisFenByPly = positions
            analysisPly = tv.uciMoves.size
            analysisFen = positions.getOrNull(analysisPly) ?: analysisFen
''',
    "live extension cache update"
)

# Direct CoC handoff should not always overwrite the clipboard. Keep clipboard
# only as a fallback if the temporary URI cannot be prepared or CoC is absent.
text = replace_once(
    text,
    '''        val pgn = tvPgn(tv)
        copyTvPgnToClipboard(context, pgn)
        val uri = tvPgnContentUri(context, tvPgnName(tv), pgn)
        if (uri == null) {
            Toast.makeText(
                context,
                "PGN copied, but the temporary PGN file could not be prepared.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
''',
    '''        val pgn = tvPgn(tv)
        val uri = tvPgnContentUri(context, tvPgnName(tv), pgn)
        if (uri == null) {
            copyTvPgnToClipboard(context, pgn)
            Toast.makeText(
                context,
                "The direct PGN handoff could not be prepared. PGN copied as a fallback.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
''',
    "direct handoff clipboard behavior"
)

text = replace_once(
    text,
    '''        if (opened) {
            Toast.makeText(context, "PGN copied and opened in Chess Openings Coach.", Toast.LENGTH_LONG).show()
        } else {
            context.openChessOpeningsCoachPlayStore()
            Toast.makeText(context, "Chess Openings Coach was not found. The PGN is still on the clipboard.", Toast.LENGTH_LONG).show()
        }
''',
    '''        if (opened) {
            Toast.makeText(context, "Opening this game in Chess Openings Coach PGN Reader…", Toast.LENGTH_LONG).show()
        } else {
            copyTvPgnToClipboard(context, pgn)
            context.openChessOpeningsCoachPlayStore()
            Toast.makeText(context, "Chess Openings Coach was not found. PGN copied as a fallback.", Toast.LENGTH_LONG).show()
        }
''',
    "handoff result message"
)

TV.write_text(text, encoding="utf-8")
print("Applied Chess TV cached navigation and direct PGN handoff UX patch")
