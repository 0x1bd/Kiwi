package org.kvxd.kiwi.path

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import org.kvxd.kiwi.world.BlockProfile
import org.kvxd.kiwi.world.PlayerBox
import org.kvxd.kiwi.world.Stances
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

class PathSearch(private val ctx: PathContext) {

    private val heap = NodeHeap(4096)
    private val known = Long2ObjectOpenHashMap<PathNode>(16384)
    private val closed = LongOpenHashSet(16384)
    private val buffer = MoveBuffer()
    private val generator = MoveGenerator(ctx)

    private var originX = 0
    private var originY = 0
    private var originZ = 0

    fun search(start: BlockPos, startFeetY: Double, goal: PathGoal): PathResult {
        val startedAt = System.nanoTime()

        heap.clear()
        known.clear()
        closed.clear()

        originX = start.x
        originY = start.y
        originZ = start.z

        val startNode = createStartNode(start, startFeetY, goal)
            ?: return PathResult(Path.EMPTY, PathFailure.NoStartStance, 0, 0, elapsed(startedAt))

        heap.add(startNode)
        known.put(key(startNode.x, startNode.y, startNode.z, startNode.placements), startNode)

        var best = startNode
        var bestH = startNode.h
        var expanded = 0
        var iterations = 0
        var reached: PathNode? = null
        var hitLimit = false

        while (!heap.isEmpty()) {
            iterations++
            if (iterations > ctx.maxSearchIterations) {
                hitLimit = true
                break
            }

            val current = heap.poll() ?: break
            val currentKey = key(current.x, current.y, current.z, current.placements)
            if (!closed.add(currentKey)) continue
            expanded++

            if (current.h < bestH) {
                bestH = current.h
                best = current
            }

            if (goal.isReached(current.x, current.y, current.z)) {
                reached = current
                break
            }

            expand(current, goal)
        }

        val duration = elapsed(startedAt)

        if (reached != null) {
            return PathResult(Path(reconstruct(reached), PathStatus.COMPLETE), null, expanded, iterations, duration)
        }

        if (best !== startNode) {
            val failure = if (hitLimit) PathFailure.SearchLimit else PathFailure.NoRoute
            return PathResult(Path(reconstruct(best), PathStatus.PARTIAL), failure, expanded, iterations, duration)
        }

        val failure = when {
            hitLimit -> PathFailure.SearchLimit
            !ctx.view.isKnown(goal.approximateTarget().x, goal.approximateTarget().y, goal.approximateTarget().z) ->
                PathFailure.Unloaded

            else -> PathFailure.NoRoute
        }
        return PathResult(Path.EMPTY, failure, expanded, iterations, duration)
    }

    private fun expand(current: PathNode, goal: PathGoal) {
        generator.generate(current, buffer)

        val parent = current.parent
        for (i in 0 until buffer.size) {
            val kind = buffer.kind[i] ?: continue
            val nx = buffer.x[i]
            val ny = buffer.y[i]
            val nz = buffer.z[i]
            val nFeet = buffer.feetY[i]
            val breaks = buffer.breaks[i] ?: NO_BREAKS
            val place = buffer.place[i]

            val breakCount = breaks.size
            if (current.broken + breakCount > MoveCosts.MAX_PATH_BREAKS) continue

            val placements = current.placements + if (place != NO_PLACE) 1 else 0
            val broken = current.broken + breakCount
            val nodeKey = key(nx, ny, nz, placements)
            if (closed.contains(nodeKey)) continue

            var linkParent = current
            var cost = current.g + buffer.cost[i] +
                breakCount * current.broken * MoveCosts.BREAK_ESCALATION

            if (parent != null &&
                kind.smoothable &&
                breaks.isEmpty() &&
                place == NO_PLACE &&
                parent.placements == current.placements &&
                canSmooth(parent, nx, ny, nz, nFeet)
            ) {
                val direct = parent.g + distance(parent, nx, nz, nFeet)
                if (direct < cost) {
                    linkParent = parent
                    cost = direct
                }
            }

            val existing = known.get(nodeKey)
            if (existing == null) {
                val node = PathNode(
                    x = nx,
                    y = ny,
                    z = nz,
                    feetY = nFeet,
                    kind = kind,
                    breaks = breaks,
                    place = place,
                    placements = placements,
                    broken = broken,
                    g = cost,
                    h = goal.heuristic(nx, ny, nz),
                    parent = linkParent
                )
                known.put(nodeKey, node)
                heap.add(node)
            } else if (cost < existing.g - 1.0E-9) {
                existing.g = cost
                existing.broken = broken
                existing.parent = linkParent
                existing.kind = kind
                existing.breaks = breaks
                existing.place = place
                existing.feetY = nFeet
                heap.update(existing)
            }
        }
    }

    private fun canSmooth(parent: PathNode, nx: Int, ny: Int, nz: Int, nFeet: Double): Boolean {
        if (parent.feetY != nFeet) return false
        if (abs(parent.x - nx) + abs(parent.z - nz) < 2) return false
        return Corridor.isWalkable(ctx.view, parent.x, parent.z, nx, nz, nFeet)
    }

    private fun distance(from: PathNode, nx: Int, nz: Int, nFeet: Double): Double {
        val dx = (nx - from.x).toDouble()
        val dz = (nz - from.z).toDouble()
        val dy = nFeet - from.feetY
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun createStartNode(start: BlockPos, startFeetY: Double, goal: PathGoal): PathNode? {
        val resolved = resolveStart(start, startFeetY) ?: return null
        return PathNode(
            x = resolved.first.x,
            y = resolved.first.y,
            z = resolved.first.z,
            feetY = resolved.second,
            kind = MoveKind.WALK,
            breaks = NO_BREAKS,
            place = NO_PLACE,
            placements = 0,
            broken = 0,
            g = 0.0,
            h = goal.heuristic(resolved.first.x, resolved.first.y, resolved.first.z),
            parent = null
        )
    }

    private fun resolveStart(start: BlockPos, startFeetY: Double): Pair<BlockPos, Double>? {
        val cellY = floor(startFeetY + BlockProfile.EPS).toInt()
        val candidates = intArrayOf(cellY, start.y, start.y - 1, start.y + 1)
        for (y in candidates) {
            val feet = Stances.standingFeetHeight(ctx.view, start.x, y, start.z)
            if (Stances.isValid(feet)) return BlockPos(start.x, y, start.z) to feet
        }
        for (y in candidates) {
            if (Stances.isSwimmable(ctx.view, start.x, y, start.z)) {
                return BlockPos(start.x, y, start.z) to y.toDouble()
            }
        }
        if (Stances.hasClearance(ctx.view, start.x, start.z, startFeetY, PlayerBox.HEIGHT)) {
            return start to startFeetY
        }
        return null
    }

    private fun reconstruct(node: PathNode): List<PathNode> {
        val result = ArrayList<PathNode>()
        var current: PathNode? = node
        while (current != null) {
            result.add(current)
            current = current.parent
        }
        result.reverse()
        return result
    }

    private fun key(x: Int, y: Int, z: Int, placements: Int): Long {
        val dx = (x - originX + 0x8000L) and 0xFFFF
        val dy = (y - originY + 0x400L) and 0x7FF
        val dz = (z - originZ + 0x8000L) and 0xFFFF
        val p = placements.coerceAtMost(MAX_TRACKED_PLACEMENTS).toLong()
        return (dx shl 32) or (dz shl 16) or (dy shl 3) or p
    }

    private fun elapsed(startedAt: Long): Double = (System.nanoTime() - startedAt) / 1_000_000.0
}
