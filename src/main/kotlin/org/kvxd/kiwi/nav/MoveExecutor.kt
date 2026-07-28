package org.kvxd.kiwi.nav

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.path.MoveKind
import org.kvxd.kiwi.path.PathContext
import org.kvxd.kiwi.path.PathNode
import org.kvxd.kiwi.world.LevelWorldView

sealed interface MoveOutcome {
    data object Running : MoveOutcome
    data object Done : MoveOutcome
    data class Failed(val reason: String) : MoveOutcome
}

class ExecutionContext(
    val plan: PathContext,
    val view: LevelWorldView
) {
    var ticksInMove: Int = 0
        internal set

    var isFinalMove: Boolean = false
        internal set
}

interface MoveExecutor {

    val kinds: Set<MoveKind>

    fun onEnter(ctx: ExecutionContext, from: PathNode, to: PathNode) {}

    fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome

    fun timeoutTicks(from: PathNode, to: PathNode): Int = 80
}

internal fun nodeCenter(node: PathNode): Vec3 = Vec3(node.x + 0.5, node.feetY, node.z + 0.5)
