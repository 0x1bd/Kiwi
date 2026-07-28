package org.kvxd.kiwi.bot

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos

class BotMemory {

    val placedCells = LongOpenHashSet()

    val minedCells = LongOpenHashSet()

    private val failedCells = LongOpenHashSet()

    val unobtainableItems = IntOpenHashSet()

    private val workstations = HashMap<Int, Long>()

    private var failureGeneration = 0

    fun rememberWorkstation(block: net.minecraft.world.level.block.Block, pos: BlockPos) {
        workstations[org.kvxd.kiwi.knowledge.Ids.block(block)] = pos.asLong()
    }

    fun workstation(block: net.minecraft.world.level.block.Block): BlockPos? {
        val packed = workstations[org.kvxd.kiwi.knowledge.Ids.block(block)] ?: return null
        return BlockPos.of(packed)
    }

    fun forgetWorkstation(block: net.minecraft.world.level.block.Block) {
        workstations.remove(org.kvxd.kiwi.knowledge.Ids.block(block))
    }

    fun markFailed(pos: BlockPos) {
        failedCells.add(pos.asLong())
    }

    fun hasFailed(pos: BlockPos): Boolean = failedCells.contains(pos.asLong())

    fun forgetFailures() {
        failedCells.clear()
        failureGeneration++
    }

    fun markPlaced(pos: BlockPos) {
        placedCells.add(pos.asLong())
    }

    fun markMined(pos: BlockPos) {
        minedCells.add(pos.asLong())
        failedCells.remove(pos.asLong())
    }

    fun clear() {
        placedCells.clear()
        minedCells.clear()
        failedCells.clear()
        unobtainableItems.clear()
        workstations.clear()
    }
}
