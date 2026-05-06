package org.kvxd.kiwi.agent.pathing.calc

import org.kvxd.kiwi.agent.capability.MovementCapability

data class PathResult(
    val path: NodePath?,
    val nodesVisited: Int,
    val timeComputedMs: Double,
    val iterations: Int,
    val status: PathStatus,
    val reason: PathFailureReason? = null
) {
    val isPartial: Boolean get() = status == PathStatus.PARTIAL
}

enum class PathStatus {
    COMPLETE,
    PARTIAL,
    UNREACHABLE
}

sealed class PathFailureReason {
    data object OutsideLoadedChunks : PathFailureReason()
    data class MissingCapability(val capabilities: Set<MovementCapability>) : PathFailureReason()
    data object NoLegalMoves : PathFailureReason()

    fun describe(): String = when (this) {
        OutsideLoadedChunks -> "target is outside loaded chunks"
        is MissingCapability -> "required capability disabled or missing: ${capabilities.joinToString { it.label }}"
        NoLegalMoves -> "no legal movement sequence found"
    }
}