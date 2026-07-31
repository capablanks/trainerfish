package com.tonorbe.trainerfish.pgn

/**
 * Options for emitting PGN from a [PgnTree].
 *
 * The emitter is intentionally the ONE place that decides:
 *  - move numbers ("N." / "N...")
 *  - where variations begin/end (RAV)
 *  - how comments are placed
 *
 * This file is designed to share the same token semantics as [PgnParser.kt]'s [PgnToken].
 */
data class PgnEmitOptions(
    val emitComments: Boolean = true,
    val emitVariations: Boolean = true,
    val emitMoveNumbers: Boolean = true,
    val emitResultToken: Boolean = true,
    val maxPlies: Int = 50_000
)

/** A token with an optional nodeId (for UI click mapping). */
data class PgnEmittedToken(
    val nodeId: Int?,
    val token: PgnToken
)

/**
 * Sink interface for streaming emission (useful for UI / incremental building).
 * If you don't need streaming, use [emitPgnTokensFromTree] or [emitPgnTextFromTree].
 */
interface PgnTokenSink<ID> {
    fun clear()
    fun setCurrent(currentNodeId: ID?)
    fun process(nodeId: ID?, token: PgnToken)
}

/**
 * Emit a canonical token stream from [tree].
 *
 * Notes:
 * - Tokens use [PgnToken] (same model as the parser).
 * - Move SAN tokens are emitted as [PgnToken.San] with nodeId set to the move node id.
 * - Comments are emitted as [PgnToken.BraceComment] (canonical, round-trippable).
 * - Variations are emitted as [PgnToken.RavStart]/[PgnToken.RavEnd] blocks.
 */
fun emitPgnTokensFromTree(
    tree: PgnTree,
    options: PgnEmitOptions = PgnEmitOptions(),
    currentNodeId: Int? = null
): List<PgnEmittedToken> {
    val list = ArrayList<PgnEmittedToken>(1024)
    emitPgnTokensFromTree(tree, object : PgnTokenSink<Int> {
        override fun clear() = Unit
        override fun setCurrent(currentNodeId: Int?) = Unit
        override fun process(nodeId: Int?, token: PgnToken) {
            list.add(PgnEmittedToken(nodeId, token))
        }
    }, options, currentNodeId)
    return list
}

/**
 * Streaming variant of [emitPgnTokensFromTree].
 */
fun emitPgnTokensFromTree(
    tree: PgnTree,
    out: PgnTokenSink<Int>,
    options: PgnEmitOptions = PgnEmitOptions(),
    currentNodeId: Int? = null
) {
    out.clear()
    out.setCurrent(currentNodeId)

    // Intro comment (pre-move comment)
    if (options.emitComments) {
        tree.introComment?.takeIf { it.isNotBlank() }?.let { c ->
            out.process(null, PgnToken.BraceComment(c))
        }
    }

    val root = tree.rootId
    val first = tree.nodes.getOrNull(root)?.nextId
    if (first != null) {
        emitLine(tree, out, options, startNodeId = first, startPly = 0, pliesLeft = options.maxPlies)
    }

    if (options.emitResultToken) {
        // We don't track actual result in [PgnTree]; keep canonical "*".
        out.process(null, PgnToken.Result("*"))
    }
}

private fun emitLine(
    tree: PgnTree,
    out: PgnTokenSink<Int>,
    options: PgnEmitOptions,
    startNodeId: Int,
    startPly: Int,
    pliesLeft: Int
) {
    var nodeId = startNodeId
    var ply = startPly
    var left = pliesLeft

    while (left > 0) {
        val node = tree.nodes.getOrNull(nodeId) ?: break
        ply += 1

        // Move number tokens (optional). We emit them for stable, canonical text output.
        if (options.emitMoveNumbers) {
            val moveNo = (ply + 1) / 2
            val raw = if (ply % 2 == 1) "$moveNo." else "$moveNo..."
            out.process(null, PgnToken.MoveNumber(raw))
        }

        // preComment (before this move)
        if (options.emitComments) {
            node.preComment?.takeIf { it.isNotBlank() }?.let { c ->
                out.process(nodeId, PgnToken.BraceComment(c))
            }
        }

        // move SAN as token tied to nodeId (so UI can map clicks)
        out.process(nodeId, PgnToken.San(node.san))

        // NAGs (optional)
        if (node.nags.isNotEmpty()) {
            for (nag in node.nags) {
                val n = nag.trim()
                if (n.isEmpty()) continue
                if (n.startsWith("$")) out.process(nodeId, PgnToken.Nag(n))
                else out.process(nodeId, PgnToken.NagSymbol(n))
            }
        }

        // postComment (after move)
        if (options.emitComments) {
            node.postComment?.takeIf { it.isNotBlank() }?.let { c ->
                out.process(nodeId, PgnToken.BraceComment(c))
            }
        }

        // Variations:
        // In our tree format, alternative moves ("RAVs") are stored on the PARENT node (the position before the move).
        // We want to render them AFTER the mainline move they are alternatives to (DroidFish-style),
        // so when we are on the mainline child (parent.nextId == nodeId), emit parent.variations here.
        if (options.emitVariations) {
            val pid = node.parentId
            if (pid != null) {
                val parent = tree.nodes.getOrNull(pid)
                if (parent != null && parent.nextId == nodeId && parent.variations.isNotEmpty()) {
                    for (head in parent.variations) {
                        out.process(null, PgnToken.RavStart)
                        emitLine(
                            tree,
                            out,
                            options,
                            startNodeId = head,
                            startPly = ply - 1,      // alternative to THIS move, so same ply
                            pliesLeft = left         // same remaining ply budget
                        )

                        out.process(null, PgnToken.RavEnd)
                    }
                }
            }
        }

        // Next in mainline

        val next = node.nextId ?: break
        nodeId = next
        left -= 1
    }
}

/**
 * Convert emitted tokens into a PGN movetext string (no headers).
 *
 * This is a canonical formatter:
 * - Uses single spaces between tokens (except parentheses are tight).
 * - Emits brace comments: "{ ... }"
 * - Emits RAV: "( ... )"
 */
fun emitPgnTextFromTree(
    tree: PgnTree,
    options: PgnEmitOptions = PgnEmitOptions()
): String {
    val toks = emitPgnTokensFromTree(tree, options)

    val sb = StringBuilder(8_192)
    var prevWasLParen = false
    var prevWasMoveNo = false

    fun spaceIfNeeded() {
        if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '(' && sb.last() != '\n') sb.append(' ')
    }

    for (et in toks) {
        when (val t = et.token) {
            is PgnToken.RavStart -> {
                // Ensure space before "(" unless we're already at line start.
                spaceIfNeeded()
                sb.append('(')
                prevWasLParen = true
                prevWasMoveNo = false
            }
            is PgnToken.RavEnd -> {
                // Trim trailing space before ")"
                while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
                sb.append(')')
                prevWasLParen = false
                prevWasMoveNo = false
            }
            is PgnToken.BraceComment -> {
                // Comment should be spaced like: " ... { c } ..."
                spaceIfNeeded()
                sb.append('{').append(' ')
                sb.append(t.body.trim())
                sb.append(' ').append('}')
                prevWasLParen = false
                prevWasMoveNo = false
            }
            is PgnToken.LineComment -> {
                // Canonicalize to brace comment in text output (safer).
                spaceIfNeeded()
                sb.append('{').append(' ')
                sb.append(t.body.trim())
                sb.append(' ').append('}')
                prevWasLParen = false
                prevWasMoveNo = false
            }
            is PgnToken.MoveNumber -> {
                // Move numbers always start a "group"
                if (!prevWasLParen) spaceIfNeeded()
                sb.append(t.raw)
                prevWasMoveNo = true
                prevWasLParen = false
            }
            is PgnToken.San -> {
                // After "12." or "12..." we want a space; after "(" we don't want extra leading space
                if (!prevWasMoveNo && !prevWasLParen) spaceIfNeeded()
                sb.append(t.san)
                prevWasMoveNo = false
                prevWasLParen = false
            }
            is PgnToken.Nag -> {
                spaceIfNeeded()
                sb.append(t.nag)
                prevWasMoveNo = false
                prevWasLParen = false
            }
            is PgnToken.NagSymbol -> {
                spaceIfNeeded()
                sb.append(t.sym)
                prevWasMoveNo = false
                prevWasLParen = false
            }
            is PgnToken.Result -> {
                spaceIfNeeded()
                sb.append(t.value)
                prevWasMoveNo = false
                prevWasLParen = false
            }
            is PgnToken.Other -> {
                spaceIfNeeded()
                sb.append(t.raw)
                prevWasMoveNo = false
                prevWasLParen = false
            }

            is PgnToken.NullMove -> {
                // Print null move used by some book PGNs: "--"
                spaceIfNeeded()
                sb.append("--")
                prevWasLParen = false
                prevWasMoveNo = false
            }
        }
    }

    return sb.toString()
        .replace(Regex("\\s+"), " ")
        .trim()
}
