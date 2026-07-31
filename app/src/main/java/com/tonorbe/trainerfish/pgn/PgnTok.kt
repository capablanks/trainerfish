package com.tonorbe.trainerfish.pgn

/**
 * DroidFish-style PGN token model.
 * This is the shared "vocabulary" between:
 *  - Tokenizer (read)
 *  - Parser (build tree) [we'll add next]
 *  - Emitters (walk tree -> tokens)
 *  - Renderers/exporters (tokens -> UI text / PGN text)
 */
sealed interface PgnTok {
    data object LBracket : PgnTok
    data object RBracket : PgnTok
    data object LParen : PgnTok
    data object RParen : PgnTok
    data object Period : PgnTok
    data object Asterisk : PgnTok

    data class Integer(val n: Int) : PgnTok
    data class Symbol(val s: String) : PgnTok
    data class StringLit(val s: String) : PgnTok
    data class Nag(val n: Int) : PgnTok
    data class Comment(val s: String) : PgnTok

    data object Eof : PgnTok
}

/** Canonical tag used for clickable moves in Compose text. */
const val PGN_NODE_TAG: String = "pgn_node"
