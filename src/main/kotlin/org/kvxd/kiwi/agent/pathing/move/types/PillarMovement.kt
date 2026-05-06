package org.kvxd.kiwi.agent.pathing.move.types

import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.move.AbstractMovement
import org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil
import org.kvxd.kiwi.player

object PillarMovement : AbstractMovement(MovementType.PILLAR) {

    private const val COST = 6.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigData.allowPillar) return
        if (ConfigData.allowedBuildBlockTypes.isEmpty()) return

        val hasBlocks = (0..8).any { slot ->
            val stack = player.inventory.getItem(slot)
            val blockItem = stack.item as? BlockItem ?: return@any false
            blockItem.block in ConfigData.allowedBuildBlockTypes
        }

        if (!hasBlocks) return

        val dest = current.pos.above()

        val blocksToBreak = listOf(dest, dest.above())
        val miningPlan = MovementObstructionUtil.planMining(blocksToBreak)

        if (miningPlan != null) {
            output.append(dest, current, target, COST, miningPlan = miningPlan)
        }
    }
}