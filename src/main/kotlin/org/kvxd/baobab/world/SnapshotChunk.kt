package org.kvxd.baobab.world

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

class SnapshotChunk {

    private val blocks = ConcurrentHashMap<Int, BlockState>()

    fun set(pos: BlockPos, state: BlockState) {
        blocks[localIndex(pos)] = state
    }

    fun get(pos: BlockPos): BlockState? {
        return blocks[localIndex(pos)]
    }

    private fun localIndex(pos: BlockPos): Int {
        val x = pos.x and 15
        val z = pos.z and 15
        val y = (pos.y + 64) and 511
        return (y shl 8) or (x shl 4) or z
    }
}