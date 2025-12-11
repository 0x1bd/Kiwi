package org.kvxd.kiwi.pathing.cache

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.level.block.PointedDripstoneBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.WitherRoseBlock
import net.minecraft.world.level.block.state.BlockState
import org.kvxd.kiwi.level

object CollisionCache {

    private const val UNCACHED: Byte = -1

    const val PASSABLE: Byte = 0
    const val SOLID: Byte = 1
    const val WATER: Byte = 2
    const val LAVA: Byte = 3
    const val DANGER: Byte = 4
    const val UNSTABLE: Byte = 5

    private val cache = ThreadLocal.withInitial {
        Long2ByteOpenHashMap(8192).apply { defaultReturnValue(UNCACHED) }
    }

    fun clearCache() = cache.get().clear()

    fun hasState(pos: BlockPos, vararg types: Byte): Boolean {
        val result = resolve(pos.x, pos.y, pos.z)
        return types.any { it == result }
    }

    fun isPassable(pos: BlockPos) = hasState(pos, PASSABLE)

    fun isSolid(pos: BlockPos) = hasState(pos, SOLID, UNSTABLE)
    fun isSolid(x: Int, y: Int, z: Int) = resolve(x, y, z) == SOLID || resolve(x, y, z) == UNSTABLE

    fun isSafeToMine(pos: BlockPos) = hasState(pos, SOLID)

    fun isDangerous(pos: BlockPos) = hasState(pos, DANGER)

    fun isWalkable(pos: BlockPos): Boolean {
        val x = pos.x;
        val y = pos.y;
        val z = pos.z

        if (resolve(x, y, z) != PASSABLE) return false
        if (resolve(x, y + 1, z) != PASSABLE) return false

        val ground = resolve(x, y - 1, z)
        return ground == SOLID || ground == UNSTABLE
    }

    fun isObstructed(pos: BlockPos): Boolean {
        return CollisionCache.isSolid(pos) || CollisionCache.isDangerous(pos)
    }

    private fun resolve(x: Int, y: Int, z: Int): Byte {
        val key = BlockPos.asLong(x, y, z)
        val map = cache.get()
        val cached = map.get(key)
        if (cached != UNCACHED) return cached

        val pos = BlockPos(x, y, z)
        val state = level.getBlockState(pos)
        val computed = computeState(state, pos)

        map.put(key, computed)
        return computed
    }

    private fun computeState(state: BlockState, pos: BlockPos): Byte {
        val fluid = state.fluidState
        if (!fluid.isEmpty) {
            if (fluid.`is`(FluidTags.LAVA)) return LAVA
            if (fluid.`is`(FluidTags.WATER)) return WATER
        }

        val block = state.block
        if (block is FireBlock ||
            block is MagmaBlock ||
            block is CactusBlock ||
            block is CampfireBlock ||
            block is SweetBerryBushBlock ||
            block is WitherRoseBlock ||
            block is PowderSnowBlock
        ) return DANGER

        if (state.getCollisionShape(level, pos).isEmpty) return PASSABLE

        val above = level.getBlockState(pos.above()).block
        if (above is FallingBlock ||
            above is ScaffoldingBlock ||
            above is PointedDripstoneBlock
        ) return UNSTABLE

        return SOLID
    }
}