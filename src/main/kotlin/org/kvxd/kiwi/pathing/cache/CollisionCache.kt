package org.kvxd.kiwi.pathing.cache

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.world

object CollisionCache {

    private val cache = ThreadLocal.withInitial {
        Long2ByteOpenHashMap(4096).apply {
            defaultReturnValue(-1)
        }
    }

    fun clearCache() {
        cache.get().clear()
    }

    inline fun isWalkable(pos: BlockPos): Boolean {
        val x = pos.x
        val y = pos.y
        val z = pos.z

        return !isSolid(x, y, z) &&
                !isSolid(x, y + 1, z) &&
                isSolid(x, y - 1, z)
    }

    fun isSolid(x: Int, y: Int, z: Int): Boolean {
        val key = BlockPos.asLong(x, y, z)
        val map = cache.get()

        val cached = map.get(key)
        if (cached != (-1).toByte()) {
            return cached == 1.toByte()
        }

        val pos = BlockPos(x, y, z)
        val state = world.getBlockState(pos)

        val solid = if (!state.getCollisionShape(world, pos).isEmpty)
            1.toByte()
        else
            0.toByte()

        map.put(key, solid)
        return solid == 1.toByte()
    }

    fun isSolid(pos: BlockPos): Boolean =
        isSolid(pos.x, pos.y, pos.z)
}