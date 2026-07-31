package com.tonorbe.trainerfish.pgn

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Token receiver that builds a DroidFish-like move list as AnnotatedString.
 * - Variations are indented by nest level
 * - Mainline paragraphs are bold
 * - Comments are green and whitespace-compacted
 * - Moves are clickable via PGN_NODE_TAG annotation
 *
 * IMPORTANT: Do not set a forced text color in ClickableText(style=...) when using this.
 */
class PgnComposeTextReceiver(
    private val fontSize: TextUnit = 14.sp,
    private val indentStep: TextUnit = 12.sp,
    private val moveColor: Color = Color.White,
    private val metaColor: Color = Color(0xFF59D65A),
    private val currentBg: Color = Color(0xFF666666),
) : PgnTokenReceiver<Int> {

    private var currentNode: Int? = null

    private val movesStyle = SpanStyle(color = moveColor)
    private val metaStyle = SpanStyle(color = metaColor)
    private val commentStyle = SpanStyle(color = metaColor)
    private val currentStyle = SpanStyle(background = currentBg)

    private var built: AnnotatedString = AnnotatedString("")
    fun getText(): AnnotatedString = built

    override fun clear() {
        built = AnnotatedString("")
    }

    override fun setCurrent(nodeRef: Int?) {
        currentNode = nodeRef
    }

    override fun process(nodeRef: Int?, tok: PgnTok) {
        // We rebuild in one pass using buildAnnotatedString,
        // so process() buffers tokens and finalizes in finish().
        // For simplicity in this first drop-in, we store tokens and build at finish().
        tokenBuffer.add(TokenItem(nodeRef, tok))
    }

    fun finish() {
        val items = tokenBuffer.toList()
        tokenBuffer.clear()

        built = buildAnnotatedString {
            var nestLevel = 0
            var col0 = true
            var pendingNewLine = false

            var paraStart = 0
            var paraIndent = 0
            var paraBold = false

            fun compactInlineWs(s: String): String = s.replace(Regex("[ \t\r\n]+"), " ").trim()

            fun sanitizeVisible(s: String): String {
                var out = s
                // Remove common Chessable / internal tags like @@StartFen@@ ... @@EndFen@@
                out = out.replace(Regex("@@[^@\n]{1,80}@@"), "")
                // Strip PGN clock/eval tags if present: {[%clk 0:10:23]} etc.
                out = out.replace(Regex("""\[%[^\]]+\]"""), "")
                // Collapse odd leftovers
                return out
            }


            fun splitParagraphs(raw: String): List<String> {
                // Keep paragraph intent: blank lines mean new paragraph.
                return raw.split(Regex("""\n\s*\n+"""))
                    .map { compactInlineWs(sanitizeVisible(it)) }
                    .filter { it.isNotBlank() }
            }


            fun finalizeParagraph(eof: Boolean) {
                if (!col0) {
                    val paraEnd = length

                    if (paraIndent > 0) {
                        val indent = indentStep * paraIndent
                        addStyle(
                            ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent)),
                            paraStart,
                            paraEnd
                        )
                    }
                    if (paraBold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), paraStart, paraEnd)

                    if (!eof) append('\n')

                    paraStart = length
                    paraIndent = nestLevel
                    paraBold = false
                }
                col0 = true
            }

            fun newLine(eof: Boolean = false) = finalizeParagraph(eof)

            fun blankLine() {
                // Add an empty line between paragraphs.
                append('\n')
                paraStart = length
                paraIndent = nestLevel
                paraBold = false
                col0 = true
            }

            fun appendMeta(s: String) {
                if (s.isEmpty()) return
                pushStyle(metaStyle); append(s); pop()
            }

fun appendComment(raw: String) {
    val paras = splitParagraphs(raw)
    if (paras.isEmpty()) return

    // DroidFish-ish: top-level comments become block lines (indented one level)
    if (nestLevel == 0) {
        nestLevel++
        newLine()
        nestLevel--
    } else {
        if (!col0) appendMeta(" ")
    }

    paras.forEachIndexed { idx, t ->
        if (idx > 0) {
            // Start a new paragraph visually (and make sure there's separation after a period)
            newLine()
            blankLine()
        }

        pushStyle(commentStyle); append(t); pop()
        col0 = false
    }

    if (nestLevel == 0) newLine()
}

            fun nagToGlyph(n: Int): String {
                // Common PGN NAG glyphs (DroidFish-like). Unknown NAGs fall back to "$n".
                return when (n) {
                    0 -> ""               // null annotation
                    1 -> "!"
                    2 -> "?"
                    3 -> "!!"
                    4 -> "??"
                    5 -> "!?"
                    6 -> "?!"
                    7 -> "□"              // forced move (often rendered as a box)
                    8 -> "□"              // singular move

                    10 -> "="             // equal
                    13 -> "∞"             // unclear position
                    14 -> "+="            // slight advantage white
                    15 -> "=+"            // slight advantage black
                    16 -> "+/-"           // advantage white
                    17 -> "-/+"           // advantage black
                    18 -> "+-"            // winning for white
                    19 -> "-+"            // winning for black
                    20 -> "∞"             // with compensation

                    else -> "$$n"
                }
            }

            fun appendMove(nodeId: Int, san: String) {
                if (!col0) appendMeta(" ")

                pushStringAnnotation(PGN_NODE_TAG, nodeId.toString())
                if (currentNode != null && currentNode == nodeId) pushStyle(currentStyle)
                pushStyle(movesStyle)
                append(san)
                pop() // movesStyle
                if (currentNode != null && currentNode == nodeId) pop() // currentStyle
                pop() // annotation

                col0 = false
                if (nestLevel == 0) paraBold = true
            }

            for ((nodeRef, tok) in items) {
                if (pendingNewLine) {
                    // match DroidFish: after ')' only insert newline if next isn't ')'
                    if (tok !is PgnTok.RParen) newLine()
                    pendingNewLine = false
                }

                when (tok) {
                    is PgnTok.LParen -> {
                        nestLevel++
                        if (col0) paraIndent++
                        newLine()
                        appendMeta("(")
                        col0 = false
                    }
                    is PgnTok.RParen -> {
                        appendMeta(")")
                        nestLevel = (nestLevel - 1).coerceAtLeast(0)
                        pendingNewLine = true
                        col0 = false
                    }
                    is PgnTok.LBracket -> { if (!col0) appendMeta(" "); appendMeta("["); col0 = false }
                    is PgnTok.RBracket -> { appendMeta("]"); newLine(); col0 = true }
                    is PgnTok.Period -> { appendMeta("."); col0 = false }
                    is PgnTok.Asterisk -> { if (!col0) appendMeta(" "); appendMeta("*"); col0 = false }
                    is PgnTok.Integer -> {
                        if (!col0) appendMeta(" ")
                        appendMeta(tok.n.toString())
                        col0 = false
                    }
                    is PgnTok.StringLit -> {
                        if (!col0) appendMeta(" ")
                        appendMeta("\"${tok.s}\"")
                        col0 = false
                    }
                    is PgnTok.Nag -> {
                        if (!col0) appendMeta(" ")
                        val glyph = nagToGlyph(tok.n)
                        if (glyph.isNotEmpty()) appendMeta(glyph)
                        col0 = false
                    }
                    is PgnTok.Comment -> appendComment(tok.s)
                    is PgnTok.Symbol -> {
                        val id = nodeRef
                        if (id != null) appendMove(id, tok.s)
                        else {
                            if (!col0) appendMeta(" ")
                            appendMeta(tok.s)
                            col0 = false
                        }
                    }
                    is PgnTok.Eof -> newLine(eof = true)
                }
            }

            newLine(eof = true)
        }
    }

    private data class TokenItem(val nodeRef: Int?, val tok: PgnTok)
    private val tokenBuffer = ArrayList<TokenItem>(2048)
}