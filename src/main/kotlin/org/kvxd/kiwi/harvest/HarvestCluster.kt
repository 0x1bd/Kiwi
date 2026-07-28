package org.kvxd.kiwi.harvest

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.kvxd.kiwi.bot.BotMemory
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.scan.BlockScan
import kotlin.math.sqrt

class HarvestCluster(
    val blocks: List<BlockPos>,
    val anchor: BlockPos,
    val distance: Double,
    val breakTicks: Double
) {
    val size: Int get() = blocks.size

    val breakTicksPerBlock: Double get() = if (blocks.isEmpty()) 0.0 else breakTicks / blocks.size

    fun miningOrder(from: BlockPos): List<BlockPos> =
        blocks.sortedWith(compareBy({ from.distSqr(it) }, { it.y }))

    fun cheapest(cost: (BlockPos) -> Double): BlockPos? {
        var best: BlockPos? = null
        var bestScore = Double.MAX_VALUE
        for (pos in blocks) {
            val score = cost(pos)
            if (score.isInfinite()) continue
            if (score < bestScore) {
                bestScore = score
                best = pos
            }
        }
        return best
    }

    fun contains(pos: BlockPos): Boolean = blocks.any { it == pos }

    override fun toString(): String = "cluster(${blocks.size} blocks @ [${anchor.x},${anchor.y},${anchor.z}])"
}

class HarvestOption(
    val cluster: HarvestCluster,
    val score: Double
)

object HarvestPlanner {

    const val SWITCH_MARGIN = 12.0

    private const val MAX_CLUSTER_SIZE = 192
    private const val TRAVEL_WEIGHT = 1.0
    private const val VERTICAL_WEIGHT = 1.6
    private const val BREAK_WEIGHT = 0.05
    private const val YIELD_WEIGHT = 6.0

    fun sourceBlocks(wantedItems: IntArray, tools: List<ItemStack>): IntOpenHashSet {
        val result = IntOpenHashSet()
        for (item in wantedItems) {
            for (harvest in Knowledge.sourcesOf(item)) {
                val block = Ids.blockById(harvest.block) ?: continue
                val state = block.defaultBlockState()
                if (state.requiresCorrectToolForDrops() && tools.none { it.isCorrectToolForDrops(state) }) continue
                result.add(harvest.block)
            }
        }
        return result
    }

    fun allSourceBlocks(wantedItems: IntArray): IntOpenHashSet {
        val result = IntOpenHashSet()
        for (item in wantedItems) {
            for (harvest in Knowledge.sourcesOf(item)) result.add(harvest.block)
        }
        return result
    }

    fun missingTools(wantedItems: IntArray, tools: List<ItemStack>): List<String> {
        val needed = LinkedHashSet<String>()
        for (item in wantedItems) {
            for (harvest in Knowledge.sourcesOf(item)) {
                val block = Ids.blockById(harvest.block) ?: continue
                val state = block.defaultBlockState()
                if (!state.requiresCorrectToolForDrops()) continue
                if (tools.any { it.isCorrectToolForDrops(state) }) continue
                needed.addAll(harvest.acceptableToolNames())
            }
        }
        return needed.toList()
    }

    fun findClusters(
        level: Level,
        origin: BlockPos,
        radius: Int,
        sources: IntOpenHashSet,
        memory: BotMemory,
        breakTicksOf: (BlockState, BlockPos) -> Double,
        maxClusters: Int = 12
    ): List<HarvestCluster> {
        if (sources.isEmpty()) return emptyList()

        val matches: (BlockState) -> Boolean = { state -> sources.contains(Ids.block(state.block)) }
        val hits = BlockScan.find(level, origin, radius, radius.coerceAtMost(48), 3072, matches)
        if (hits.isEmpty()) return emptyList()

        val available = LongOpenHashSet(hits.size)
        for (hit in hits) {
            if (memory.hasFailed(hit.pos)) continue
            available.add(hit.pos.asLong())
        }

        val visited = LongOpenHashSet(available.size)
        val clusters = ArrayList<HarvestCluster>()

        for (hit in hits.sortedBy { it.distanceSq }) {
            val key = hit.pos.asLong()
            if (!available.contains(key) || !visited.add(key)) continue

            val members = floodFill(hit.pos, available, visited)
            if (members.isEmpty()) continue

            var closest = members[0]
            var closestDistSq = origin.distSqr(closest)
            var ticks = 0.0
            for (member in members) {
                val distSq = origin.distSqr(member)
                if (distSq < closestDistSq) {
                    closestDistSq = distSq
                    closest = member
                }
                ticks += breakTicksOf(level.getBlockState(member), member)
            }

            clusters.add(HarvestCluster(members, closest, sqrt(closestDistSq), ticks))
            if (clusters.size >= maxClusters) break
        }

        return clusters
    }

    fun score(cluster: HarvestCluster, origin: BlockPos, stillNeeded: Int): Double {
        val vertical = kotlin.math.abs(cluster.anchor.y - origin.y).toDouble()
        val horizontal = kotlin.math.sqrt(
            ((cluster.anchor.x - origin.x).toDouble() * (cluster.anchor.x - origin.x) +
                (cluster.anchor.z - origin.z).toDouble() * (cluster.anchor.z - origin.z))
        )

        val taken = minOf(cluster.size, stillNeeded.coerceAtLeast(1))
        val travel = horizontal * TRAVEL_WEIGHT + vertical * VERTICAL_WEIGHT
        val work = cluster.breakTicksPerBlock * taken * BREAK_WEIGHT
        val yield = taken * YIELD_WEIGHT

        return travel + work - yield
    }

    fun best(clusters: List<HarvestCluster>, origin: BlockPos, stillNeeded: Int): HarvestOption? =
        clusters.map { HarvestOption(it, score(it, origin, stillNeeded)) }.minByOrNull { it.score }

    private fun floodFill(start: BlockPos, available: LongOpenHashSet, visited: LongOpenHashSet): List<BlockPos> {
        val members = ArrayList<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(start)
        members.add(start)

        while (queue.isNotEmpty() && members.size < MAX_CLUSTER_SIZE) {
            val current = queue.removeFirst()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val neighbour = current.offset(dx, dy, dz)
                        val key = neighbour.asLong()
                        if (!available.contains(key)) continue
                        if (!visited.add(key)) continue
                        members.add(neighbour)
                        queue.add(neighbour)
                    }
                }
            }
        }

        return members
    }
}
