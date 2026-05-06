package org.kvxd.kiwi.agent.pathing.calc

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.pathing.calc.structs.MinHeap
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.pathing.move.MovementProvider
import org.kvxd.kiwi.level
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

        var finalPath: NodePath? = null
        var found = false
        var bestFrontierNode: Node? = null

        val approximateTarget = goal.getApproximateTarget()
        val targetOutsideLoadedChunks = !level.isLoaded(approximateTarget)

        while (!openSet.isEmpty()) {
            iterations++

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

            if (isLoadedChunkFrontier(current.pos, approximateTarget) && current.costH < hStart) {
                bestFrontierNode = current
                break
            }

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

        val missingCapabilities = PathSearchDiagnostics.missingCapabilities()
        val status: PathStatus
        val reason: PathFailureReason?

        if (found) {
            finalPath = reconstructPath(bestNode, false)
            status = PathStatus.COMPLETE
            reason = null
        } else if (bestFrontierNode != null && bestFrontierNode != startNode) {
            finalPath = reconstructPath(bestFrontierNode, true)
            status = PathStatus.PARTIAL
            reason = PathFailureReason.OutsideLoadedChunks
        } else {
            status = PathStatus.UNREACHABLE
            reason = when {
                missingCapabilities.isNotEmpty() -> PathFailureReason.MissingCapability(missingCapabilities)
                nodesVisited <= 1 -> PathFailureReason.NoLegalMoves
                targetOutsideLoadedChunks -> PathFailureReason.OutsideLoadedChunks
                else -> PathFailureReason.NoLegalMoves
            }
        }

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0

        return PathResult(
            path = finalPath,
            nodesVisited = nodesVisited,
            timeComputedMs = durationMs,
            iterations = iterations,
            status = status,
            reason = reason
        )
    }

    private fun isLoadedChunkFrontier(pos: BlockPos, target: BlockPos): Boolean {
        val currentDistance = pos.distSqr(target)

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue

                val neighbor = pos.offset(dx, 0, dz)
                if (!level.isLoaded(neighbor) && neighbor.distSqr(target) < currentDistance) {
                    return true
                }
            }
        }
        return false
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