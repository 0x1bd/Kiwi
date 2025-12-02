package org.kvxd.baobab.pathing.move

import net.minecraft.util.math.BlockPos

enum class MoveType {
    WALK,
    JUMP,
    DROP
}

object MovementAnalysis {

    fun analyze(start: BlockPos, end: BlockPos): MoveType {
        val dy = end.y - start.y

        return when {
            dy > 0 -> MoveType.JUMP
            dy < 0 -> MoveType.DROP
            else -> MoveType.WALK
        }
    }
}