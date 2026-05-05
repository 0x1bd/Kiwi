package org.kvxd.kiwi.agent.pathing.calc

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.pathing.calc.structs.MinHeap
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.pathing.move.MovementProvider
import kotlin.math.sqrt

class ThetaStar {

    private val nodeRegistry = HashMap<Long, Node>(16384)
    private val openSet = MinHeap()
    private val closedSet = HashSet<Long>(16384)
    private val neighborBuffer = ArrayList<Node>(64)

    fun calculate(start: BlockPos, goal: Goal): PathResult {
        val startTime = System.nanoTime()

        openSet.clear()
        closedSet.clear()
        nodeRegistry.clear()

        val hStart = goal.getHeuristic(start)

        val startNode = MovementProvider.getStartNode(start, hStart)

        openSet.add(startNode)
        nodeRegistry[startNode.posLong] = startNode

        var bestNode: Node = startNode
        var bestH = startNode.costH

        var iterations = 0
        var nodesVisited = 0

        val maxOps = ConfigData.maxIterations

        var finalPath: NodePath? = null
        var found = false
        var isPartial = false

        while (!openSet.isEmpty()) {
            if (iterations++ > maxOps) {
                if (bestNode != startNode) {
                    isPartial = true
                }
                break
            }

            val current = openSet.poll() ?: break
            nodesVisited++

            if (current.costH < bestH) {
                bestH = current.costH
                bestNode = current
            }

            if (goal.hasReached(current.pos)) {
                bestNode = current
                found = true
                break
            }

            val currentLong = current.posLong
            if (!closedSet.add(currentLong)) continue

            neighborBuffer.clear()
            MovementProvider.getNeighbors(current, goal.getApproximateTarget(), neighborBuffer)

            for (i in 0 until neighborBuffer.size) {
                val neighborNode = neighborBuffer[i]
                val nPosLong = neighborNode.posLong

                if (closedSet.contains(nPosLong)) continue

                val parent = current.parent

                var potentialG: Double
                var potentialParent: Node?

                val canSmooth = parent != null &&
                        neighborNode.type.isSmoothable &&
                        LineOfSight.check(parent, neighborNode)

                if (canSmooth) {
                    val dist = sqrt(parent.pos.distSqr(neighborNode.pos))
                    potentialG = parent.costG + dist + neighborNode.miningCost
                    potentialParent = parent
                } else {
                    potentialG = neighborNode.costG
                    potentialParent = current
                }

                var finalType = neighborNode.type
                if (finalType == MovementType.TRAVEL || finalType == MovementType.JUMP) {
                    if (neighborNode.pos.y > potentialParent.pos.y) {
                        finalType = MovementType.JUMP
                    }
                }

                val existingNode = nodeRegistry[nPosLong]
                val hCost = goal.getHeuristic(neighborNode.pos) * 1.001

                if (existingNode == null) {
                    val newNode = neighborNode.copy(
                        costG = potentialG,
                        costH = hCost,
                        parent = potentialParent,
                        type = finalType
                    )

                    openSet.add(newNode)
                    nodeRegistry[nPosLong] = newNode
                } else {
                    if (potentialG < existingNode.costG) {
                        existingNode.costG = potentialG
                        existingNode.parent = potentialParent
                        existingNode.type = finalType
                        existingNode.miningBlocks = neighborNode.miningBlocks
                        existingNode.miningCost = neighborNode.miningCost

                        openSet.update(existingNode)
                    }
                }
            }
        }

        if (found || isPartial) {
            finalPath = reconstructPath(bestNode, isPartial)
        }

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0

        return PathResult(
            path = finalPath,
            nodesVisited = nodesVisited,
            timeComputedMs = durationMs,
            iterations = iterations,
            isPartial = isPartial
        )
    }

    private fun reconstructPath(node: Node, isPartial: Boolean): NodePath {
        val list = ArrayList<Node>()
        var curr: Node? = node
        while (curr != null) {
            list.add(curr)
            curr = curr.parent
        }
        list.reverse()
        return NodePath(list, isPartial)
    }
}
