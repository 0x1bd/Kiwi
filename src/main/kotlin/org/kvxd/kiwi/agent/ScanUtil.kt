package org.kvxd.kiwi.agent

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player

object ScanUtil {

    data class BlockScanTarget(
        val pos: BlockPos,
        val block: Block,
        val distance: Double
    )

    data class ScanResult(
        val targets: List<BlockScanTarget>,
        val searchRadius: Int,
        val timeMs: Double
    )

    private val blockNameCache = mutableMapOf<String, Block?>()
    private val knownPositions = mutableMapOf<String, MutableSet<BlockPos>>()
    private var lastKnownOrigin = BlockPos.ZERO
    private const val POSITION_STALE_DISTANCE = 32

    fun resolveBlock(blockName: String): Block? {
        return blockNameCache.getOrPut(blockName) {
            BuiltInRegistries.BLOCK.firstOrNull {
                BuiltInRegistries.BLOCK.getKey(it).path == blockName
            }
        }
    }

    fun findNearestByName(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        blockName: String
    ): BlockScanTarget? {
        val block = resolveBlock(blockName) ?: return null
        return findNearestByType(origin, radius, block)
    }

    fun findNearestByType(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        blockType: Block
    ): BlockScanTarget? {
        val cached = getCachedPosition(origin, blockType)
        if (cached != null) {
            val state = level.getBlockState(cached)
            if (state.block == blockType) {
                val dx = cached.x - origin.x
                val dy = cached.y - origin.y
                val dz = cached.z - origin.z
                val dist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                return BlockScanTarget(cached, blockType, dist)
            }
        }

        return scanExpandingShell(origin, radius, setOf(blockType))
    }

    fun findNearest(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        blockFilter: (Block) -> Boolean
    ): BlockScanTarget? {
        return scanExpandingShell(origin, radius, blockFilter = blockFilter)
    }

    fun findNearestBlockType(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        type: Block
    ): BlockScanTarget? {
        return findNearestByType(origin, radius, type)
    }

    fun findNearestByNames(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        names: Set<String>
    ): BlockScanTarget? {
        val blockSet = names.mapNotNull { resolveBlock(it) }.toSet()
        if (blockSet.isEmpty()) return null
        return scanExpandingShell(origin, radius, blockSet)
    }

    fun scanNearby(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 64,
        blockFilter: (Block) -> Boolean,
        maxResults: Int = 50
    ): ScanResult {
        val startTime = System.nanoTime()
        val results = mutableListOf<BlockScanTarget>()

        for (r in 0..radius) {
            val minX = origin.x - r
            val maxX = origin.x + r
            val minZ = origin.z - r
            val maxZ = origin.z + r
            val minY = (origin.y - r).coerceAtLeast(level.dimensionType().minY())
            val maxY = (origin.y + r).coerceAtMost(level.dimensionType().minY() + level.dimensionType().height())

            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (r > 0 && x != minX && x != maxX && z != minZ && z != maxZ) continue

                    for (y in minY..maxY) {
                        val pos = BlockPos(x, y, z)
                        val block = level.getBlockState(pos).block

                        if (blockFilter(block)) {
                            val dx = x - origin.x
                            val dy = y - origin.y
                            val dz = z - origin.z
                            val dist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                            results.add(BlockScanTarget(pos, block, dist))
                            if (results.size >= maxResults) break
                        }
                    }
                    if (results.size >= maxResults) break
                }
                if (results.size >= maxResults) break
            }
            if (results.size >= maxResults) break
        }

        val endTime = System.nanoTime()
        return ScanResult(
            targets = results,
            searchRadius = radius,
            timeMs = (endTime - startTime) / 1_000_000.0
        )
    }

    private fun scanExpandingShell(
        origin: BlockPos,
        radius: Int,
        blockSet: Set<Block>? = null,
        blockFilter: ((Block) -> Boolean)? = null
    ): BlockScanTarget? {
        val minY = (origin.y - radius).coerceAtLeast(level.dimensionType().minY())
        val maxY = (origin.y + radius).coerceAtMost(level.dimensionType().minY() + level.dimensionType().height())

        for (r in 0..radius) {
            val minX = origin.x - r
            val maxX = origin.x + r
            val minZ = origin.z - r
            val maxZ = origin.z + r
            val minYS = (origin.y - r).coerceAtLeast(minY)
            val maxYS =
                (origin.y + r).coerceAtMost(maxY)
            var best: BlockScanTarget? = null
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (r > 0 && x != minX && x != maxX && z != minZ && z != maxZ) continue

                    for (y in minYS..maxYS) {
                        val pos = BlockPos(x, y, z)
                        val block = level.getBlockState(pos).block

                        val matches = when {
                            blockSet != null -> block in blockSet
                            blockFilter != null -> blockFilter(block)
                            else -> false
                        }

                        if (matches) {
                            val dx = x - origin.x
                            val dy = y - origin.y
                            val dz = z - origin.z
                            val dist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                            val candidate = BlockScanTarget(pos, block, dist)
                            if (best == null || candidate.distance < best.distance) {
                                best = candidate
                            }
                        }
                    }
                }
            }

            if (best != null) {
                cachePosition(origin, best.block, best.pos)
                return best
            }
        }

        return null
    }

    private fun cachePosition(origin: BlockPos, block: Block, pos: BlockPos) {
        val name = BuiltInRegistries.BLOCK.getKey(block).path
        knownPositions.getOrPut(name) { mutableSetOf() }.add(pos)
        lastKnownOrigin = origin
    }

    private fun getCachedPosition(origin: BlockPos, block: Block): BlockPos? {
        if (origin.distSqr(lastKnownOrigin) > POSITION_STALE_DISTANCE * POSITION_STALE_DISTANCE) {
            knownPositions.clear()
            return null
        }
        val name = BuiltInRegistries.BLOCK.getKey(block).path
        return knownPositions[name]?.firstOrNull()
    }

    fun findNearestDroppedItem(
        origin: BlockPos = player.blockPosition(),
        radius: Int = 32,
        itemId: String
    ): ItemEntity? {
        val aabb = AABB.ofSize(Vec3.atCenterOf(origin), radius * 2.0, radius * 2.0, radius * 2.0)
        return level.getEntities(null, aabb) { it is ItemEntity }
            .map { it as ItemEntity }
            .filter {
                val stack = it.item
                if (stack.isEmpty) return@filter false
                val id = BuiltInRegistries.ITEM.getKey(stack.item).path
                id == itemId
            }
            .minByOrNull { it.distanceToSqr(player.x, player.y, player.z) }
    }

    fun clearCache() {
        knownPositions.clear()
        blockNameCache.clear()
    }
}
