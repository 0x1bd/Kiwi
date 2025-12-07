package org.kvxd.kiwi.pathing.cache

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import net.minecraft.block.AbstractFireBlock
import net.minecraft.block.BlockState
import net.minecraft.block.CactusBlock
import net.minecraft.block.CampfireBlock
import net.minecraft.block.MagmaBlock
import net.minecraft.block.PowderSnowBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.block.WitherRoseBlock
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.world

object CollisionCache {

    private const val UNCACHED: Byte = -1
    private const val PASSABLE: Byte = 0
    private const val SOLID: Byte = 1
    private const val WATER: Byte = 2
    private const val LAVA: Byte = 3
    private const val DANGER: Byte = 4

    private val cache = ThreadLocal.withInitial {
        Long2ByteOpenHashMap(8192).apply { defaultReturnValue(UNCACHED) }
    }

    fun clearCache() = cache.get().clear()

    fun isPassable(pos: BlockPos): Boolean {
        return resolve(pos.x, pos.y, pos.z) == PASSABLE
    }

    fun isSolid(pos: BlockPos): Boolean = resolve(pos.x, pos.y, pos.z) == SOLID

    fun isSolid(x: Int, y: Int, z: Int): Boolean = resolve(x, y, z) == SOLID

    fun isWalkable(pos: BlockPos): Boolean {
        val x = pos.x;
        val y = pos.y;
        val z = pos.z
        return resolve(x, y, z) == PASSABLE &&
                resolve(x, y + 1, z) == PASSABLE &&
                resolve(x, y - 1, z) == SOLID
    }

    private fun resolve(x: Int, y: Int, z: Int): Byte {
        val key = BlockPos.asLong(x, y, z)
        val map = cache.get()
        val cached = map.get(key)
        if (cached != UNCACHED) return cached

        val pos = BlockPos(x, y, z)
        val state = world.getBlockState(pos)
        val computed = computeState(state, pos)
        map.put(key, computed)
        return computed
    }

    private fun computeState(state: BlockState, pos: BlockPos): Byte {
        if (!state.fluidState.isEmpty) {
            if (state.fluidState.isIn(FluidTags.LAVA)) return LAVA
            if (state.fluidState.isIn(FluidTags.WATER)) return WATER
        }

        val block = state.block
        if (block is AbstractFireBlock
            || block is MagmaBlock
            || block is CactusBlock
            || block is CampfireBlock
            || block is SweetBerryBushBlock
            || block is WitherRoseBlock
            || block is PowderSnowBlock
        ) return DANGER

        if (state.getCollisionShape(world, pos).isEmpty) return PASSABLE

        return SOLID
    }
}