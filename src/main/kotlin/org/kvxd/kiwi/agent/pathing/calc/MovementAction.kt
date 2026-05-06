package org.kvxd.kiwi.agent.pathing.calc

import net.minecraft.core.BlockPos

sealed interface MovementAction {
    val type: MovementType
    val target: BlockPos
    val miningBlocks: List<BlockPos>
    val miningCost: Double

    fun withType(type: MovementType): MovementAction
}

data class PlannedMovementAction(
    override val type: MovementType,
    override val target: BlockPos,
    override val miningBlocks: List<BlockPos> = emptyList(),
    override val miningCost: Double = 0.0
) : MovementAction {

    override fun withType(type: MovementType): MovementAction =
        copy(type = type)
}