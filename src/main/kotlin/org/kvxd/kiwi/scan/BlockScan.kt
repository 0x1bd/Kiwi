package org.kvxd.kiwi.scan

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.util.PriorityQueue

class BlockHit(
    val pos: BlockPos,
    val state: BlockState,
    val distanceSq: Double
)

object BlockScan {

    private class Candidate(
        val chunkX: Int,
        val chunkZ: Int,
        val sectionY: Int,
        val minDistanceSq: Double
    )

    fun find(
        level: Level,
        origin: BlockPos,
        radiusXZ: Int,
        radiusY: Int = radiusXZ,
        limit: Int = 512,
        matches: (BlockState) -> Boolean
    ): List<BlockHit> {
        if (limit <= 0) return emptyList()

        val minY = (origin.y - radiusY).coerceAtLeast(level.minY)
        val maxY = (origin.y + radiusY).coerceAtMost(level.minY + level.height - 1)
        val radiusXZSq = (radiusXZ.toDouble() + 0.5) * (radiusXZ.toDouble() + 0.5)

        val candidates = sectionsByDistance(origin, radiusXZ, minY, maxY)
        val farthestFirst = PriorityQueue<BlockHit>(minOf(limit, 1024)) { a, b ->
            b.distanceSq.compareTo(a.distanceSq)
        }

        for (candidate in candidates) {
            if (farthestFirst.size >= limit) {
                val worst = farthestFirst.peek() ?: break
                if (candidate.minDistanceSq >= worst.distanceSq) break
            }

            val chunk = level.getChunk(candidate.chunkX, candidate.chunkZ, ChunkStatus.FULL, false) ?: continue
            val index = chunk.getSectionIndexFromSectionY(candidate.sectionY)
            if (index < 0 || index >= chunk.sections.size) continue
            val section = chunk.sections[index]
            if (section.hasOnlyAir()) continue
            if (!runCatching { section.states.maybeHas(matches) }.getOrDefault(true)) continue

            val baseX = candidate.chunkX shl 4
            val baseZ = candidate.chunkZ shl 4
            val baseY = SectionPos.sectionToBlockCoord(candidate.sectionY)

            for (localY in 0..15) {
                val worldY = baseY + localY
                if (worldY < minY || worldY > maxY) continue
                val dy = (worldY - origin.y).toDouble()

                for (localX in 0..15) {
                    val worldX = baseX + localX
                    val dx = (worldX - origin.x).toDouble()
                    if (dx * dx > radiusXZSq) continue

                    for (localZ in 0..15) {
                        val worldZ = baseZ + localZ
                        val dz = (worldZ - origin.z).toDouble()
                        if (dx * dx + dz * dz > radiusXZSq) continue

                        val state = section.getBlockState(localX, localY, localZ)
                        if (state.isAir || !matches(state)) continue

                        val distanceSq = dx * dx + dy * dy + dz * dz
                        if (farthestFirst.size >= limit) {
                            val worst = farthestFirst.peek() ?: continue
                            if (distanceSq >= worst.distanceSq) continue
                            farthestFirst.poll()
                        }
                        farthestFirst.add(BlockHit(BlockPos(worldX, worldY, worldZ), state, distanceSq))
                    }
                }
            }
        }

        return farthestFirst.sortedBy { it.distanceSq }
    }

    fun nearest(
        level: Level,
        origin: BlockPos,
        radiusXZ: Int,
        radiusY: Int = radiusXZ,
        matches: (BlockState) -> Boolean
    ): BlockHit? = find(level, origin, radiusXZ, radiusY, 1, matches).firstOrNull()

    private fun sectionsByDistance(origin: BlockPos, radiusXZ: Int, minY: Int, maxY: Int): List<Candidate> {
        val minChunkX = (origin.x - radiusXZ) shr 4
        val maxChunkX = (origin.x + radiusXZ) shr 4
        val minChunkZ = (origin.z - radiusXZ) shr 4
        val maxChunkZ = (origin.z + radiusXZ) shr 4
        val minSection = SectionPos.blockToSectionCoord(minY)
        val maxSection = SectionPos.blockToSectionCoord(maxY)

        val candidates = ArrayList<Candidate>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1) * 4)
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                for (sectionY in minSection..maxSection) {
                    val dx = axisDistance(origin.x, chunkX shl 4)
                    val dz = axisDistance(origin.z, chunkZ shl 4)
                    val dy = axisDistance(origin.y, SectionPos.sectionToBlockCoord(sectionY))
                    candidates.add(Candidate(chunkX, chunkZ, sectionY, dx * dx + dy * dy + dz * dz))
                }
            }
        }
        candidates.sortBy { it.minDistanceSq }
        return candidates
    }

    private fun axisDistance(value: Int, sectionStart: Int): Double = when {
        value < sectionStart -> (sectionStart - value).toDouble()
        value > sectionStart + 15 -> (value - sectionStart - 15).toDouble()
        else -> 0.0
    }
}
