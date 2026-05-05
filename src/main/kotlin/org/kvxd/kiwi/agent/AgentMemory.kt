package org.kvxd.kiwi.agent

import net.minecraft.core.BlockPos

data class AgentMemory(
    val craftableItemIds: MutableSet<String> = mutableSetOf(),
    val minedPositions: MutableSet<BlockPos> = mutableSetOf(),
    val minedItemIds: MutableSet<String> = mutableSetOf(),
    val placedPositions: MutableSet<BlockPos> = mutableSetOf(),
    val knownBlocks: MutableMap<String, MutableList<BlockPos>> = mutableMapOf(),
    val failedBlockPositions: MutableMap<String, MutableSet<BlockPos>> = mutableMapOf(),
    val blockedCraftItems: MutableSet<String> = mutableSetOf(),
    val blockedMineItems: MutableSet<String> = mutableSetOf()
) {
    fun markFailedBlock(blockId: String, pos: BlockPos) {
        failedBlockPositions.getOrPut(blockId) { mutableSetOf() }.add(pos)
    }

    fun isFailedBlock(blockId: String, pos: BlockPos): Boolean {
        return pos in failedBlockPositions[blockId].orEmpty()
    }
}
