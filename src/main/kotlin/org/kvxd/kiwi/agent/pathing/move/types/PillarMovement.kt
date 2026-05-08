package org.kvxd.kiwi.agent.pathing.move.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.move.AbstractMovement
import org.kvxd.kiwi.agent.pathing.move.MovementCosts
import org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil

object PillarMovement : AbstractMovement(MovementType.PILLAR) {

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigData.allowPillar) return
        if (ConfigData.allowedBuildBlockTypes.isEmpty()) return

        if (current.pillarBlocks <= 0) return

        val dest = current.pos.above()

        val blocksToBreak = listOf(dest, dest.above())
        val miningPlan = MovementObstructionUtil.planMining(blocksToBreak)

        if (miningPlan != null) {
            output.append(dest, current, target, MovementCosts.PILLAR, miningPlan = miningPlan)
        }
    }
}
