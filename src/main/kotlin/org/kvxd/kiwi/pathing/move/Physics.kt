package org.kvxd.kiwi.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.world.WorldSnapshot

object Physics {

    /**
     * Checks if a block is safe to stand on (Solid ground, Air at feet, Air at head).
     */
    fun isWalkable(pos: BlockPos): Boolean {
        return !isSolid(pos) &&
                !isSolid(pos.up()) &&
                isSolid(pos.down())
    }

    /**
     * Checks if a block obstructs movement.
     */
    fun isSolid(pos: BlockPos): Boolean {
        val state = WorldSnapshot.getBlock(pos)
        return state.isOpaqueFullCube || state.blocksMovement()
    }

}