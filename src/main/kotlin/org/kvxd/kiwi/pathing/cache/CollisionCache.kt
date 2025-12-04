package org.kvxd.kiwi.pathing.cache

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.world

object CollisionCache {

    private val cache = ThreadLocal.withInitial { HashMap<Long, Boolean>(4096) }

    fun clearCache() {
        cache.get().clear()
    }

    fun isWalkable(pos: BlockPos): Boolean {
        return !isSolid(pos) &&
                !isSolid(pos.up()) &&
                isSolid(pos.down())
    }

    fun isSolid(pos: BlockPos): Boolean {
        val map = cache.get()
        val key = pos.asLong()

        if (map.containsKey(key)) {
            return map[key]!!
        }

        val state = world.getBlockState(pos)
        val isSolid = !state.getCollisionShape(world, pos).isEmpty

        map[key] = isSolid
        return isSolid
    }
}