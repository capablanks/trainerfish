package com.tonorbe.trainerfish.pgn

/**
 * Single authoritative tokenizer for PGN.
 * Converts raw characters -> PgnTok stream.
 *
 * Notes:
 *  - Supports {...} comments (no nesting)
 *  - Supports ; line comments
 *  - Supports "..." strings (basic backslash escapes)
 *  - Supports $123 NAG
 *  - Emits Integer only when the entire token is digits
 */
class PgnTokenizer(private val text: String) {
    private var i: Int = 0
    private val n: Int = text.length
    private var pushed: PgnTok? = null

    fun pushBack(tok: PgnTok) {
        require(pushed == null) { "Only one token pushBack supported" }
        pushed = tok
    }

    fun next(): PgnTok {
        pushed?.let { t -> pushed = null; return t }

        skipWs()
        if (i >= n) return PgnTok.Eof

        return when (val c = text[i]) {
            '[' -> { i++; PgnTok.LBracket }
            ']' -> { i++; PgnTok.RBracket }
            '(' -> { i++; PgnTok.LParen }
            ')' -> { i++; PgnTok.RParen }
            '.' -> { i++; PgnTok.Period }
            '*' -> { i++; PgnTok.Asterisk }

            '{' -> readBraceComment()
            ';' -> readLineComment()

            '"' -> readString()

            '$' -> readNag()

            else -> readWordOrInt()
        }
    }

    private fun skipWs() {
        while (i < n) {
            val c = text[i]
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') i++ else break
        }
    }

    private fun readBraceComment(): PgnTok {
        // Consume '{', read until '}' or EOF
        i++ // skip '{'
        val start = i
        while (i < n && text[i] != '}') i++
        val s = text.substring(start, i.coerceAtMost(n))
        if (i < n && text[i] == '}') i++ // skip '}'
        return PgnTok.Comment(s)
    }

    private fun readLineComment(): PgnTok {
        // Consume ';', read until newline or EOF
        i++ // skip ';'
        val start = i
        while (i < n && text[i] != '\n') i++
        val s = text.substring(start, i.coerceAtMost(n))
        return PgnTok.Comment(s)
    }

    private fun readString(): PgnTok {
        // Consume opening quote
        i++
        val sb = StringBuilder()
        while (i < n) {
            val c = text[i++]
            when (c) {
                '"' -> break
                '\\' -> {
                    if (i < n) {
                        // basic escape: \" \\ \n \t \r
                        val e = text[i++]
                        sb.append(
                            when (e) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                '"', '\\' -> e
                                else -> e
                            }
                        )
                    }
                }
                else -> sb.append(c)
            }
        }
        return PgnTok.StringLit(sb.toString())
    }

    private fun readNag(): PgnTok {
        i++ // skip '$'
        val start = i
        while (i < n && text[i].isDigit()) i++
        val num = text.substring(start, i).toIntOrNull() ?: 0
        return PgnTok.Nag(num)
    }

    private fun readWordOrInt(): PgnTok {
        val start = i
        while (i < n) {
            val c = text[i]
            // terminate on whitespace or any PGN delimiter
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') break
            if (c == '.' || c == '[' || c == ']' || c == '(' || c == ')' ||
                c == '{' || c == '}' || c == ';' || c == '"' || c == '$' || c == '*'
            ) break
            i++
        }
        val s = text.substring(start, i)
        if (s.isNotEmpty() && s.all { it.isDigit() }) {
            return PgnTok.Integer(s.toInt())
        }
        return PgnTok.Symbol(s)
    }
}
