package org.kvxd.kiwi.agent.pathing.move

import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.pathing.calc.MAX_TRACKED_PILLAR_BLOCKS
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.registryPath

object PathInventoryTracker {

    fun initialPillarBlocks(): Int {
        return InventoryUtil.fullInventory.sumOf { stack ->
            val blockItem = stack.item as? BlockItem ?: return@sumOf 0
            if (blockItem.block !in ConfigData.allowedBuildBlockTypes) return@sumOf 0
            if (blockItem.block.registryPath in Agent.context.minedItemIds) return@sumOf 0
            stack.count
        }.coerceAtMost(MAX_TRACKED_PILLAR_BLOCKS)
    }

    fun afterMovement(parent: Node, type: MovementType, minedBlocks: List<BlockPos>): Int {
        val gained = minedBlocks.distinct().sumOf(::guaranteedPillarDrops)
        val spent = if (type == MovementType.PILLAR) 1 else 0
        return (parent.pillarBlocks + gained - spent).coerceIn(0, MAX_TRACKED_PILLAR_BLOCKS)
    }

    private fun guaranteedPillarDrops(pos: BlockPos): Int {
        val state = level.getBlockState(pos)
        val harvest = HarvestDatabase.getForBlock(state.block) ?: return 0
        if (harvest.dropCount.first <= 0) return 0
        if (harvest.primaryDropId !in ConfigData.allowedBuildBlockIds) return 0
        if (harvest.primaryDropId in Agent.context.minedItemIds) return 0
        if (state.requiresCorrectToolForDrops() && !hasCorrectToolForDrops(state)) return 0
        return harvest.dropCount.first
    }

    private fun hasCorrectToolForDrops(state: net.minecraft.world.level.block.state.BlockState): Boolean {
        if (player.hasCorrectToolForDrops(state)) return true
        return InventoryUtil.fullInventory.any { stack ->
            !stack.isEmpty && stack.isCorrectToolForDrops(state)
        }
    }
}
