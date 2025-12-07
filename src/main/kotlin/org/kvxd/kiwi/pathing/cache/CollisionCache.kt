package org.kvxd.kiwi.pathing.cache

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import net.minecraft.block.*
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.world

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
        val x = pos.x; val y = pos.y; val z = pos.z

        if (resolve(x, y, z) != PASSABLE) return false
        if (resolve(x, y + 1, z) != PASSABLE) return false

        val ground = resolve(x, y - 1, z)
        return ground == SOLID || ground == UNSTABLE
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
        val fluid = state.fluidState
        if (!fluid.isEmpty) {
            if (fluid.isIn(FluidTags.LAVA)) return LAVA
            if (fluid.isIn(FluidTags.WATER)) return WATER
        }

        val block = state.block
        if (block is AbstractFireBlock ||
            block is MagmaBlock ||
            block is CactusBlock ||
            block is CampfireBlock ||
            block is SweetBerryBushBlock ||
            block is WitherRoseBlock ||
            block is PowderSnowBlock
        ) return DANGER

        if (state.getCollisionShape(world, pos).isEmpty) return PASSABLE

        val above = world.getBlockState(pos.up()).block
        if (above is FallingBlock ||
            above is ScaffoldingBlock ||
            above is PointedDripstoneBlock
        ) return UNSTABLE

        return SOLID
    }
}