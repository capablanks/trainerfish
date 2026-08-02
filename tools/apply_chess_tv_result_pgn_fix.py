from pathlib import Path
import re

C=Path('app/src/main/java/com/tonorbe/trainerfish/LichessTvClient.kt')
S=Path('app/src/main/java/com/tonorbe/trainerfish/LichessTvScreen.kt')

def one(t,a,b,n):
    if t.count(a)!=1: raise SystemExit(f'{n}: {t.count(a)} matches')
    return t.replace(a,b,1)
def sub(t,p,r,n):
    t2,k=re.subn(p,r,t,count=1,flags=re.S)
    if k!=1: raise SystemExit(f'{n}: {k} matches')
    return t2

c=C.read_text()
c=one(c,
'''                if (!isCurrent(serial)) break
                if (!_state.value.watchedGameOngoing) break
                throw IllegalStateException("Player game stream ended")
''',
'''                if (!isCurrent(serial)) break
                fetchCurrentPgn(activeGameId)?.let { loaded ->
                    if (isCurrent(serial)) applyFetchedPgn(activeGameId, loaded)
                }
                if (!_state.value.watchedGameOngoing || _state.value.result != null) break
                throw IllegalStateException("Player game stream ended")
''','final PGN refresh')

c=sub(c,r'''    private fun handleWatchedGameDescription\(.*?\n    \}\n\n    private fun handleWatchedFen''','''    private fun handleWatchedGameDescription(
        serial: Long,
        username: String,
        expectedGameId: String,
        data: JSONObject
    ) {
        if (!isCurrent(serial)) return
        val id = data.optString("id").trim().ifBlank { expectedGameId }
        if (id != expectedGameId) return
        val players = data.optJSONObject("players")
        val white = parseStreamPlayer(players?.optJSONObject("white"), "White")
        val black = parseStreamPlayer(players?.optJSONObject("black"), "Black")
        val statusName = data.optJSONObject("status")?.optString("name").orEmpty()
            .ifBlank { data.optString("status") }.lowercase()
        val ongoing = statusName.isBlank() || statusName == "created" || statusName == "started"
        val startFen = data.optString("initialFen").trim().ifBlank { LICHESS_TV_START_FEN }
        val fen = normalizeStreamFen(data.optString("fen"), startFen)
        val lastMove = data.optString("lastMove").trim().takeIf { it.isNotBlank() }
        val existing = _state.value.takeIf { it.gameId == id }
        val result = lichessTvResultOrNull(data.optString("result"))
            ?: when (data.optString("winner").trim().lowercase()) {
                "white" -> "1-0"; "black" -> "0-1"; else -> null
            }
            ?: if (!ongoing && statusName in setOf(
                "draw", "stalemate", "repetition", "insufficientmaterial", "fiftymoves"
            )) "1/2-1/2" else existing?.result

        _state.value = LichessTvState(
            status = if (ongoing) LichessTvConnectionStatus.LIVE else LichessTvConnectionStatus.FINISHED,
            source = LichessTvSource.WATCHED_PLAYER,
            watchedUsername = username,
            watchedGameOngoing = ongoing,
            gameId = id,
            fen = fen,
            startFen = existing?.startFen ?: startFen,
            white = white,
            black = black,
            orientationWhite = white.name.equals(username, ignoreCase = true),
            lastMoveUci = lastMove ?: existing?.lastMoveUci,
            uciMoves = existing?.uciMoves.orEmpty(),
            sanMoves = existing?.sanMoves.orEmpty(),
            pgnLoaded = existing?.pgnLoaded ?: false,
            result = result,
            noticeMessage = if (ongoing) {
                "Watching @$username • 3-move delay • engine off"
            } else {
                "@$username finished • engine analysis available"
            }
        )
        pgnJob?.cancel()
        pgnJob = scope.launch {
            val loaded = if (ongoing) fetchPlayerCurrentPgn(username) else fetchCurrentPgn(id)
            loaded?.let { applyFetchedPgn(id, it) }
        }
    }

    private fun handleWatchedFen''','watched result')

c=sub(c,r'''    private fun handleWatchedFen\(.*?\n    \}\n\n    private fun parseStreamPlayer''','''    private fun handleWatchedFen(serial: Long, data: JSONObject) {
        if (!isCurrent(serial)) return
        val moveUci = data.optString("lm").trim().takeIf { it.isNotBlank() }
        _state.update { current ->
            if (current.source != LichessTvSource.WATCHED_PLAYER) return@update current
            val before = runCatching {
                com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(current.fen) }
            }.getOrNull()
            val move = if (before != null && moveUci != null) bfUciToMoveOnBoard(before, moveUci) else null
            val san = if (before != null && move != null) runCatching {
                bfPrettySan(before, move, before.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE)
            }.getOrNull() else null
            val applied = before != null && move != null && runCatching { before.doMove(move) }.getOrDefault(false)
            val fen = when {
                applied -> before!!.fen
                moveUci == null -> normalizeStreamFen(data.optString("fen"), current.fen)
                else -> current.fen
            }
            val append = applied && moveUci != null && current.lastMoveUci != moveUci &&
                current.uciMoves.lastOrNull() != moveUci
            val finished = current.status == LichessTvConnectionStatus.FINISHED || current.result != null
            current.copy(
                status = if (finished) LichessTvConnectionStatus.FINISHED else LichessTvConnectionStatus.LIVE,
                watchedGameOngoing = !finished,
                fen = fen,
                white = current.white.copy(seconds = data.optIntOrNull("wc") ?: current.white.seconds),
                black = current.black.copy(seconds = data.optIntOrNull("bc") ?: current.black.seconds),
                lastMoveUci = if (applied) moveUci else current.lastMoveUci,
                uciMoves = if (append) current.uciMoves + moveUci else current.uciMoves,
                sanMoves = if (append) current.sanMoves + (san ?: moveUci) else current.sanMoves,
                errorMessage = null
            )
        }
    }

    private fun parseStreamPlayer''','legal stream moves')

c=one(c,
'''            whiteSeconds = whiteSeconds,
            blackSeconds = blackSeconds
        )
''',
'''            whiteSeconds = whiteSeconds,
            blackSeconds = blackSeconds,
            result = lichessTvResultOrNull(pgnTag(pgn, "Result"))
        )
''','parse result')

c=sub(c,r'''    private fun applyFetchedPgn\(.*?\n    \}\n\n    @Synchronized''','''    private fun applyFetchedPgn(gameId: String, fetched: LoadedTvPgn) {
        _state.update { current ->
            if (current.gameId != gameId) return@update current
            val currentFen = positionAfter(fetched.startFen, current.uciMoves)
            val extend = currentFen != null && current.uciMoves.size >= fetched.uciMoves.size &&
                current.uciMoves.take(fetched.uciMoves.size) == fetched.uciMoves
            val uci = if (extend) current.uciMoves else fetched.uciMoves
            val san = if (extend && current.sanMoves.size == current.uciMoves.size) current.sanMoves else fetched.sanMoves
            val finalFen = (if (extend) currentFen else positionAfter(fetched.startFen, fetched.uciMoves)) ?: current.fen
            val result = fetched.result ?: current.result
            val finished = result != null
            val watchedFinished = current.source == LichessTvSource.WATCHED_PLAYER && finished
            current.copy(
                status = if (finished) LichessTvConnectionStatus.FINISHED else current.status,
                watchedGameOngoing = if (watchedFinished) false else current.watchedGameOngoing,
                fen = finalFen,
                startFen = fetched.startFen,
                lastMoveUci = uci.lastOrNull() ?: current.lastMoveUci,
                uciMoves = uci,
                sanMoves = san,
                white = current.white.copy(seconds = fetched.whiteSeconds ?: current.white.seconds),
                black = current.black.copy(seconds = fetched.blackSeconds ?: current.black.seconds),
                pgnLoaded = true,
                result = result,
                errorMessage = null,
                noticeMessage = if (watchedFinished) {
                    current.watchedUsername?.let { "@$it finished • engine analysis available" } ?: current.noticeMessage
                } else current.noticeMessage
            )
        }
    }

    @Synchronized''','canonical PGN')

c=one(c,
'''        val whiteSeconds: Int?,
        val blackSeconds: Int?
    )
''',
'''        val whiteSeconds: Int?,
        val blackSeconds: Int?,
        val result: String?
    )
''','result field')
C.write_text(c)

s=S.read_text()
s=one(s,'import androidx.activity.compose.BackHandler\n','''import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
''','activity imports')
s=one(s,
'''import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
''',
'''import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
''','export imports')

anchor='''private enum class LichessBroadcastBrowserMode {
    TOURNAMENTS,
    COUNTRY,
    FAVORITES
}

'''
helpers=anchor+'''private fun tvTag(s: String) = s.replace("\\\\", "\\\\\\\\").replace("\\\"", "\\\\\\\"")
    .replace(Regex("[\\r\\n]+"), " ").trim()
private fun tvPgn(s: LichessTvState): String {
    val r=lichessTvResultOrNull(s.result) ?: "*"
    val d=SimpleDateFormat("yyyy.MM.dd",Locale.US).format(Date())
    val e=when(s.source){
        LichessTvSource.WATCHED_PLAYER->"TrainerFish Watch Player"
        LichessTvSource.BROADCAST_BOARD->s.noticeMessage?.substringBefore(" • ")?.takeIf{it.isNotBlank()} ?: "TrainerFish Broadcast"
        LichessTvSource.TOP_GAME->"TrainerFish Chess TV"
    }
    val site=s.gameId?.takeIf{it.isNotBlank()}?.let{"https://lichess.org/$it"} ?: "Lichess"
    return buildString {
        appendLine("[Event \\\"${tvTag(e)}\\\"]"); appendLine("[Site \\\"${tvTag(site)}\\\"]")
        appendLine("[Date \\\"$d\\\"]"); appendLine("[Round \\\"-\\\"]")
        appendLine("[White \\\"${tvTag(s.white.name)}\\\"]"); appendLine("[Black \\\"${tvTag(s.black.name)}\\\"]")
        s.white.rating?.let{appendLine("[WhiteElo \\\"$it\\\"]")}; s.black.rating?.let{appendLine("[BlackElo \\\"$it\\\"]")}
        appendLine("[Result \\\"$r\\\"]")
        if(s.startFen.isNotBlank() && s.startFen!=LICHESS_TV_START_FEN){appendLine("[SetUp \\\"1\\\"]");appendLine("[FEN \\\"${tvTag(s.startFen)}\\\"]")}
        appendLine(); s.sanMoves.forEachIndexed{i,m->if(i>0)append(' ');if(i%2==0)append("${i/2+1}. ");append(m.trim())}
        if(s.sanMoves.isNotEmpty())append(' '); appendLine(r)
    }
}
private fun tvPgnName(s:LichessTvState):String{
    fun safe(x:String)=x.trim().replace(Regex("[^A-Za-z0-9._-]+"),"_").trim('_').take(32).ifBlank{"player"}
    val d=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())
    return "TrainerFish_${safe(s.white.name)}_vs_${safe(s.black.name)}_$d.pgn"
}

'''
s=one(s,anchor,helpers,'helpers')

s=one(s,
'''    val engineLines by ProcEngine.lines.collectAsState()

    var detached by rememberSaveable { mutableStateOf(false) }
''',
'''    val engineLines by ProcEngine.lines.collectAsState()

    var pendingPgn by remember { mutableStateOf<String?>(null) }
    val pgnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-chess-pgn")
    ) { uri ->
        val text=pendingPgn; pendingPgn=null
        if(uri!=null && !text.isNullOrBlank()) screenScope.launch {
            val ok=withContext(Dispatchers.IO){runCatching{
                context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use{it.write(text)}
                    ?: error("Unable to open file")
            }}
            Toast.makeText(context,if(ok.isSuccess)"PGN saved" else "PGN save failed",Toast.LENGTH_SHORT).show()
        }
    }

    var detached by rememberSaveable { mutableStateOf(false) }
''','launcher')

s=one(s,
'''    fun refreshBroadcasts() {
''',
'''    fun savePgn() {
        if(tv.sanMoves.isEmpty()) { Toast.makeText(context,"No moves to save yet.",Toast.LENGTH_SHORT).show(); return }
        pendingPgn=tvPgn(tv); pgnLauncher.launch(tvPgnName(tv))
    }

    fun refreshBroadcasts() {
''','save function')

s=one(s,
'''    val useDeepBroadcastAnalysis = tv.source == LichessTvSource.BROADCAST_BOARD
    val displayedFen = if (detached) analysisFen else tv.fen
    LaunchedEffect(effectiveEngineEnabled, useDeepBroadcastAnalysis, displayedFen) {
''',
'''    val useDepth50Analysis = tv.source == LichessTvSource.BROADCAST_BOARD ||
        (tv.source == LichessTvSource.WATCHED_PLAYER && !tv.watchedGameOngoing &&
            tv.status == LichessTvConnectionStatus.FINISHED)
    val displayedFen = if (detached) analysisFen else tv.fen
    LaunchedEffect(effectiveEngineEnabled, useDepth50Analysis, displayedFen) {
''','depth condition')
s=one(s,'if (useDeepBroadcastAnalysis) {','if (useDepth50Analysis) {','depth use')
s=one(s,'showBroadcastGameControls = showBroadcastGameControls,\n                    onEngineToggle',
      'showBroadcastGameControls = showBroadcastGameControls,\n                    canSavePgn = tv.sanMoves.isNotEmpty(),\n                    onEngineToggle','call flag')
s=one(s,'onTopGame = ::showTopGame,\n                    onFlip',
      'onTopGame = ::showTopGame,\n                    onSavePgn = ::savePgn,\n                    onFlip','call save')
s=one(s,'showBroadcastGameControls: Boolean,\n    onEngineToggle',
      'showBroadcastGameControls: Boolean,\n    canSavePgn: Boolean,\n    onEngineToggle','param flag')
s=one(s,'onTopGame: () -> Unit,\n    onFlip',
      'onTopGame: () -> Unit,\n    onSavePgn: () -> Unit,\n    onFlip','param save')
s=one(s,
'''                        DropdownMenuItem(
                            text = {
                                Text(
                                    when {
                                        engineLocked -> "Engine locked"
''',
'''                        DropdownMenuItem(
                            text = { Text("Save game to PGN") }, enabled = canSavePgn,
                            onClick = { controlsMenuExpanded = false; onSavePgn() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when {
                                        engineLocked -> "Engine locked"
''','menu save')
S.write_text(s)
print('applied')
