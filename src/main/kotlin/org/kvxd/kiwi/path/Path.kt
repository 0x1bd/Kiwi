package org.kvxd.kiwi.path

import net.minecraft.core.BlockPos

enum class PathStatus {
    COMPLETE,
    PARTIAL,
    FAILED
}

sealed interface PathFailure {
    val message: String

    data object NoStartStance : PathFailure {
        override val message = "no legal stance at the start position"
    }

    data object NoRoute : PathFailure {
        override val message = "no route to the goal"
    }

    data object SearchLimit : PathFailure {
        override val message = "search limit reached"
    }

    data object Unloaded : PathFailure {
        override val message = "target is outside loaded chunks"
    }

    data class Other(override val message: String) : PathFailure
}

class Path(
    val nodes: List<PathNode>,
    val status: PathStatus
) {
    val size: Int get() = nodes.size
    val isEmpty: Boolean get() = nodes.isEmpty()
    val isPartial: Boolean get() = status == PathStatus.PARTIAL

    fun node(index: Int): PathNode? = nodes.getOrNull(index)

    fun destination(): PathNode? = nodes.lastOrNull()

    fun positions(): List<BlockPos> = nodes.map { it.pos() }

    companion object {
        val EMPTY = Path(emptyList(), PathStatus.FAILED)
    }
}

class PathResult(
    val path: Path,
    val failure: PathFailure?,
    val nodesExpanded: Int,
    val iterations: Int,
    val durationMs: Double
) {
    val succeeded: Boolean get() = path.status != PathStatus.FAILED && !path.isEmpty
}
