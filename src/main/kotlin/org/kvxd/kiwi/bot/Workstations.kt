package org.kvxd.kiwi.bot

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.level
import org.kvxd.kiwi.scan.BlockScan

object Workstations {

    const val SEARCH_RADIUS = 16
    const val SEARCH_HEIGHT = 10
    private const val REMEMBERED_RANGE_SQ = 32.0 * 32.0

    fun find(memory: BotMemory, block: Block, origin: BlockPos): BlockPos? {
        memory.workstation(block)?.let { remembered ->
            if (level.getBlockState(remembered).`is`(block)) {
                if (origin.distSqr(remembered) <= REMEMBERED_RANGE_SQ) return remembered
            } else {
                memory.forgetWorkstation(block)
            }
        }

        val hit = BlockScan.nearest(level, origin, SEARCH_RADIUS, SEARCH_HEIGHT) { it.`is`(block) } ?: return null
        memory.rememberWorkstation(block, hit.pos)
        return hit.pos
    }

    fun remember(memory: BotMemory, block: Block, pos: BlockPos) {
        memory.rememberWorkstation(block, pos)
    }
}
