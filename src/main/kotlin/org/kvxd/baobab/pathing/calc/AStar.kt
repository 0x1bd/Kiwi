package org.kvxd.baobab.pathing.calc

import net.minecraft.util.math.BlockPos
import org.kvxd.baobab.config.ConfigManager
import org.kvxd.baobab.pathing.move.MovementProvider
import java.util.*

class AStar {

    fun calculate(start: BlockPos, goal: BlockPos): List<Node>? {
        val openSet = PriorityQueue<Node>()
        val closedSet = HashSet<BlockPos>()

        val nodeIndex = HashMap<BlockPos, Double>()

        val startNode = Node(start, null, 0.0, start.getSquaredDistance(goal), MovementType.WALK)
        openSet.add(startNode)
        nodeIndex[start] = 0.0

        var bestNode: Node = startNode
        var bestH = startNode.costH

        var iterations = 0

        while (openSet.isNotEmpty()) {
            if (iterations++ > ConfigManager.data.maxIterations) break

            val current = openSet.poll()

            if (current.costH < bestH) {
                bestH = current.costH
                bestNode = current
            }

            if (current.pos == goal || current.costH < 2.0) {
                return backtrack(current)
            }

            if (closedSet.contains(current.pos)) continue
            closedSet.add(current.pos)

            val neighbors = MovementProvider.getNeighbors(current, goal)
            for (neighbor in neighbors) {
                if (closedSet.contains(neighbor.pos)) continue

                val newG = neighbor.costG
                val oldG = nodeIndex[neighbor.pos] ?: Double.MAX_VALUE

                if (newG < oldG) {
                    nodeIndex[neighbor.pos] = newG
                    openSet.add(neighbor)
                }
            }
        }

        if (bestNode != startNode) {
            return backtrack(bestNode)
        }

        return null
    }

    private fun backtrack(node: Node): List<Node> {
        val path = ArrayList<Node>()
        var curr: Node? = node
        while (curr != null) {
            path.add(curr)
            curr = curr.parent
        }
        return path.reversed()
    }
}