package org.kvxd.kiwi.agent

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

data class AgentMemory(
    val craftableItemIds: MutableSet<String> = mutableSetOf(),
    val minedPositions: MutableSet<BlockPos> = mutableSetOf(),
    val minedItemIds: MutableSet<String> = mutableSetOf(),
    val placedPositions: MutableSet<BlockPos> = mutableSetOf(),
    val knownBlocks: MutableMap<Block, MutableList<BlockPos>> = mutableMapOf(),
    val failedBlockPositions: MutableMap<Block, MutableSet<BlockPos>> = mutableMapOf(),
    val blockedCraftItems: MutableSet<String> = mutableSetOf(),
    val blockedMineItems: MutableSet<String> = mutableSetOf()
) {
    fun markFailedBlock(block: Block, pos: BlockPos) {
        failedBlockPositions.getOrPut(block) { mutableSetOf() }.add(pos)
    }

    fun isFailedBlock(block: Block, pos: BlockPos): Boolean {
        return pos in failedBlockPositions[block].orEmpty()
    }
}
