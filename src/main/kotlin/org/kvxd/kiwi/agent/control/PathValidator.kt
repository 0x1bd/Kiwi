package org.kvxd.kiwi.agent.control

import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.LineOfSight
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import kotlin.math.min

object PathValidator {

    fun isPathObstructed(path: NodePath): Boolean {
        if (path.isEmpty || path.isFinished) return false

        val current = path.current() ?: return false
        val prev = path.previous()


        if (path.index > 0 && !isValidNode(current)) return true

        if (prev != null) {
            if (!validateTransition(prev, current)) return true
        }

        val lookahead = min(path.size, path.index + 3)
        var previousNodeForLookahead = current

        for (i in (path.index + 1) until lookahead) {
            val nextNode = path[i] ?: continue


            if (!isValidNode(nextNode)) return true
            if (!validateTransition(previousNodeForLookahead, nextNode)) return true

            previousNodeForLookahead = nextNode
        }

        return false
    }

    private fun isClearable(blockPos: net.minecraft.core.BlockPos): Boolean {
        if (org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isPassable(blockPos)) return true
        if (org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isDangerous(blockPos)) return false
        val cost = org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil.calculateMiningCost(listOf(blockPos))
        return cost != null
    }

    private fun isValidNode(node: Node): Boolean {
        if (node.type == MovementType.TRAVEL || node.type == MovementType.JUMP || node.type == MovementType.DROP) {
            if (!isClearable(node.pos)) return false
            if (!isClearable(node.pos.above())) return false
        }
        return true
    }

    private fun validateTransition(prev: Node, current: Node): Boolean {
        when (current.type) {
            MovementType.TRAVEL -> {
                if (prev.pos.distSqr(current.pos) > 3.0) {
                    if (!LineOfSight.check(prev, current)) return false
                }
            }

            MovementType.JUMP -> {
                if (!isClearable(prev.pos.above(2))) return false

                if (!org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isSolid(current.pos.below())) return false
            }

            MovementType.DROP -> {
                if (!isClearable(prev.pos.above())) return false
                if (!org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isSolid(current.pos.below())) return false
            }

            else -> {
                // should be handled by executors
            }
        }
        return true
    }
}