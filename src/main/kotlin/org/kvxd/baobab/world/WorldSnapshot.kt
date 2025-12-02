package org.kvxd.baobab.world

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists chunk data even when the client unloads them visually.
 * Allows pathfinding through "unloaded" areas.
 */
object WorldSnapshot {

    private val chunks = ConcurrentHashMap<Long, SnapshotChunk>()

    fun init() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> chunks.clear() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> chunks.clear() }
    }

    fun getBlock(pos: BlockPos): BlockState {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return Blocks.VOID_AIR.defaultState

        if (world.isChunkLoaded(pos.x shr 4, pos.z shr 4)) {
            val state = world.getBlockState(pos)
            cache(pos, state)
            return state
        }

        return getCached(pos) ?: Blocks.VOID_AIR.defaultState
    }

    fun clear() {
        chunks.clear()
    }

    private fun cache(pos: BlockPos, state: BlockState) {
        val chunkPos = ChunkPos.toLong(pos.x shr 4, pos.z shr 4)
        val chunk = chunks.computeIfAbsent(chunkPos) { SnapshotChunk() }
        chunk.set(pos, state)
    }

    private fun getCached(pos: BlockPos): BlockState? {
        val chunkPos = ChunkPos.toLong(pos.x shr 4, pos.z shr 4)
        return chunks[chunkPos]?.get(pos)
    }
}