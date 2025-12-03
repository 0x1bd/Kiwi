package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.structs.MinHeap
import org.kvxd.kiwi.pathing.move.MovementProvider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AStar {

    private val openSet = MinHeap()
    private val closedSet = HashSet<BlockPos>(4096)

    private val nodeRegistry = HashMap<BlockPos, Node>(4096)
    private val neighborBuffer = ArrayList<Node>(32)

    fun calculate(start: BlockPos, goal: BlockPos): PathResult {
        val startTime = System.nanoTime()

        openSet.clear()
        closedSet.clear()
        nodeRegistry.clear()

        val weight = ConfigManager.data.heuristicWeight

        val hStart = heuristic(start, goal) * weight
        val startNode = Node(start, null, 0.0, hStart, MovementType.WALK)

        openSet.add(startNode)
        nodeRegistry[start] = startNode

        var bestNode: Node = startNode
        var bestH = hStart

        var iterations = 0
        var nodesVisited = 0
        val maxOps = ConfigManager.data.maxIterations

        var finalPath: NodePath? = null

        while (!openSet.isEmpty()) {
            if (iterations++ > maxOps) break

            val current = openSet.poll() ?: break
            nodesVisited++

            val rawH = current.costH / weight
            if (rawH < ConfigManager.data.backtrackThreshold) {
                finalPath = backtrack(current)
                break
            }

            if (rawH < bestH) {
                bestH = rawH
                bestNode = current
            }

            if (!closedSet.add(current.pos)) continue

            neighborBuffer.clear()
            MovementProvider.getNeighbors(current, goal, neighborBuffer)

            for (i in 0 until neighborBuffer.size) {
                val neighbor = neighborBuffer[i]

                if (closedSet.contains(neighbor.pos)) continue

                val existingNode = nodeRegistry[neighbor.pos]

                val hCost = heuristic(neighbor.pos, goal) * weight

                if (existingNode == null) {
                    val newNode = Node(
                        neighbor.pos,
                        current,
                        neighbor.costG,
                        hCost,
                        neighbor.type
                    )

                    openSet.add(newNode)
                    nodeRegistry[neighbor.pos] = newNode
                } else {
                    if (neighbor.costG < existingNode.costG) {
                        existingNode.costG = neighbor.costG
                        existingNode.parent = current

                        openSet.update(existingNode)
                    }
                }
            }
        }

        if (finalPath == null && bestNode != startNode) {
            finalPath = backtrack(bestNode)
        }

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0

        return PathResult(finalPath, nodesVisited, durationMs, iterations)
    }

    private fun heuristic(a: BlockPos, b: BlockPos): Double {
        val dx = abs(a.x - b.x).toDouble()
        val dy = abs(a.y - b.y).toDouble()
        val dz = abs(a.z - b.z).toDouble()

        val maxD = max(dx, dz)
        val minD = min(dx, dz)

        val distXZ = (minD * 1.41421356) + (maxD - minD)
        return (distXZ + dy) * 1.001
    }

    private fun backtrack(node: Node): NodePath {
        val list = ArrayList<Node>()
        var curr: Node? = node
        while (curr != null) {
            list.add(curr)
            curr = curr.parent
        }
        list.reverse()
        return NodePath(list)
    }
}