package org.kvxd.kiwi.agent.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.PlannedMovementAction
import org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil.MiningPlan
import kotlin.math.sqrt

interface MovementStrategy {

    fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>)

    fun getStartNode(start: BlockPos): Node? = null
}

abstract class AbstractMovement(private val defaultType: MovementType) : MovementStrategy {

    protected fun MutableList<Node>.append(
        pos: BlockPos,
        parent: Node,
        target: BlockPos,
        baseCost: Double,
        typeOverride: MovementType? = null,
        miningPlan: MiningPlan? = null
    ) {
        val miningCost = miningPlan?.cost ?: 0.0
        val type = typeOverride ?: defaultType
        val g = parent.costG + baseCost + miningCost
        val h = sqrt(pos.distSqr(target))
        this.add(
            Node(
                pos = pos,
                parent = parent,
                costG = g,
                costH = h,
                action = PlannedMovementAction(
                    type = type,
                    target = pos,
                    miningBlocks = miningPlan?.blocks.orEmpty(),
                    miningCost = miningCost
                ),
                pillarBlocks = PathInventoryTracker.afterMovement(parent, type, miningPlan?.blocks.orEmpty())
            )
        )
    }
}
