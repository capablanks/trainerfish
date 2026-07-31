package com.tonorbe.trainerfish.pgn

/**
 * Equivalent to DroidFish PgnTokenReceiver.
 *
 * NodeRef is typically an Int nodeId for Trainer Fish.
 * For header/comment tokens that aren't tied to a move, nodeRef is null.
 */
interface PgnTokenReceiver<NodeRef> {
    fun clear()
    fun isUpToDate(): Boolean = true

    /**
     * Consume one token. If token corresponds to a move, nodeRef should be that move's node id.
     */
    fun process(nodeRef: NodeRef?, tok: PgnTok)

    /**
     * Optional: indicate which node is "current" for highlighting.
     * Not all receivers need to implement this.
     */
    fun setCurrent(nodeRef: NodeRef?) {}
}
