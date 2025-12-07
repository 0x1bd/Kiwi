package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.structs.MinHeap
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.pathing.move.MovementProvider

class AStar {

    private val nodeRegistry = HashMap<Long, Node>(16384)
    private val openSet = MinHeap()
    private val closedSet = HashSet<Long>(16384)
    private val neighborBuffer = ArrayList<Node>(64)

    fun calculate(start: BlockPos, goal: Goal): PathResult {
        val startTime = System.nanoTime()

        openSet.clear()
        closedSet.clear()
        nodeRegistry.clear()

        CollisionCache.clearCache()

        val hStart = goal.getHeuristic(start)
        val startNode = Node(start, null, 0.0, hStart, MovementType.WALK)

        openSet.add(startNode)
        nodeRegistry[start.asLong()] = startNode

        var bestNode: Node = startNode
        var bestH = hStart

        var iterations = 0
        var nodesVisited = 0

        val maxOps = ConfigManager.data.maxIterations

        var finalPath: NodePath?
        var found = false

        while (!openSet.isEmpty()) {
            if (iterations++ > maxOps) break

            val current = openSet.poll() ?: break
            nodesVisited++

            if (goal.hasReached(current.pos)) {
                bestNode = current
                found = true
                break
            }

            if (current.costH < bestH) {
                bestH = current.costH
                bestNode = current
            }

            val currentLong = current.posLong
            if (!closedSet.add(currentLong)) continue

            neighborBuffer.clear()
            MovementProvider.getNeighbors(current, goal.getApproximateTarget(), neighborBuffer)

            for (i in 0 until neighborBuffer.size) {
                val neighborNode = neighborBuffer[i]
                val nPosLong = neighborNode.posLong

                if (closedSet.contains(nPosLong)) continue

                val existingNode = nodeRegistry[nPosLong]

                val hCost = goal.getHeuristic(neighborNode.pos) * 1.001

                if (existingNode == null) {
                    val newNode = neighborNode.copy(costH = hCost)
                    openSet.add(newNode)
                    nodeRegistry[nPosLong] = newNode
                } else {
                    if (neighborNode.costG < existingNode.costG) {
                        existingNode.costG = neighborNode.costG
                        existingNode.parent = current
                        existingNode.type = neighborNode.type

                        openSet.update(existingNode)
                    }
                }
            }
        }

        val pathStart = bestNode
        finalPath = reconstructPath(pathStart)

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0

        val pathSize = finalPath.size

        val isValid = found || (iterations >= maxOps && pathSize > 1)

        return PathResult(
            path = if (isValid) finalPath else null,
            nodesVisited = nodesVisited,
            timeComputedMs = durationMs,
            iterations = iterations
        )
    }

    private fun reconstructPath(node: Node): NodePath {
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