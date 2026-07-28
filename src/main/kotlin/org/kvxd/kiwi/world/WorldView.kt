package org.kvxd.kiwi.world

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes

interface WorldView {

    val minY: Int

    val maxY: Int

    fun profile(x: Int, y: Int, z: Int): BlockProfile

    fun profile(pos: BlockPos): BlockProfile = profile(pos.x, pos.y, pos.z)

    fun isKnown(x: Int, y: Int, z: Int): Boolean = profile(x, y, z).known
}

class LevelWorldView(
    private val level: Level,
    private val chunkSource: (Int, Int) -> ChunkAccess? = { cx, cz ->
        level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false)
    }
) : WorldView {

    override val minY: Int = level.minY

    override val maxY: Int = level.minY + level.height

    private val chunks = Long2ObjectOpenHashMap<ChunkAccess?>(256)
    private val profiles = Long2ObjectOpenHashMap<BlockProfile>(16384)
    private val scratch = BlockPos.MutableBlockPos()

    fun invalidate(pos: BlockPos) {
        profiles.remove(pos.asLong())
    }

    fun invalidateAll() {
        profiles.clear()
        chunks.clear()
    }

    override fun profile(x: Int, y: Int, z: Int): BlockProfile {
        if (y < minY || y >= maxY) return BlockProfiles.outsideWorld

        val key = BlockPos.asLong(x, y, z)
        val cached = profiles.get(key)
        if (cached != null) return cached

        val state = blockState(x, y, z) ?: run {
            profiles.put(key, BlockProfiles.unknown)
            return BlockProfiles.unknown
        }

        scratch.set(x, y, z)
        val computed = BlockProfiles.of(state, level, scratch)
        profiles.put(key, computed)
        return computed
    }

    fun blockState(x: Int, y: Int, z: Int): BlockState? {
        val chunk = chunk(x shr 4, z shr 4) ?: return null
        val sectionIndex = chunk.getSectionIndex(y)
        val sections = chunk.sections
        if (sectionIndex < 0 || sectionIndex >= sections.size) return null
        return try {
            sections[sectionIndex].getBlockState(x and 15, y and 15, z and 15)
        } catch (_: Throwable) {
            null
        }
    }

    private fun chunk(cx: Int, cz: Int): ChunkAccess? {
        val key = SectionPos.asLong(cx, 0, cz)
        if (chunks.containsKey(key)) return chunks.get(key)
        val chunk = try {
            chunkSource(cx, cz)
        } catch (_: Throwable) {
            null
        }
        chunks.put(key, chunk)
        return chunk
    }

    val blockGetter: BlockGetter get() = level
}

fun WorldView.collides(box: AABB): Boolean {
    val minX = Math.floor(box.minX - BlockProfile.EPS).toInt()
    val maxX = Math.floor(box.maxX + BlockProfile.EPS).toInt()
    val minYc = Math.floor(box.minY - BlockProfile.EPS).toInt() - 1
    val maxYc = Math.floor(box.maxY + BlockProfile.EPS).toInt()
    val minZ = Math.floor(box.minZ - BlockProfile.EPS).toInt()
    val maxZ = Math.floor(box.maxZ + BlockProfile.EPS).toInt()

    for (x in minX..maxX) {
        for (z in minZ..maxZ) {
            for (y in minYc..maxYc) {
                val profile = profile(x, y, z)
                when (profile.shapeKind) {
                    ShapeKind.EMPTY -> continue
                    ShapeKind.FULL_CUBE -> {
                        if (!profile.known) return true
                        if (box.intersects(
                                x.toDouble(), y.toDouble(), z.toDouble(),
                                x + 1.0, y + 1.0, z + 1.0
                            )
                        ) return true
                    }

                    ShapeKind.PARTIAL -> {
                        val moved = profile.shape.move(x.toDouble(), y.toDouble(), z.toDouble())
                        if (Shapes.joinIsNotEmpty(moved, Shapes.create(box), BooleanOp.AND)) return true
                    }
                }
            }
        }
    }
    return false
}
