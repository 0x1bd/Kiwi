package org.kvxd.kiwi.control

import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.LineOfSight
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import kotlin.math.min

object PathValidator {

    fun isPathObstructed(path: NodePath): Boolean {
        if (path.isEmpty || path.isFinished) return false

        val current = path.current() ?: return false
        val prev = path.previous()

        if (!isValidNode(current)) return true

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

    private fun isValidNode(node: Node): Boolean {
        if (node.type == MovementType.TRAVEL || node.type == MovementType.JUMP) {
            if (!CollisionCache.isPassable(node.pos)) return false
            if (!CollisionCache.isPassable(node.pos.above())) return false
        }

        return true
    }

    private fun validateTransition(prev: Node, current: Node): Boolean {
        when (current.type) {
            MovementType.TRAVEL -> {
                if (!LineOfSight.check(prev, current)) return false
            }

            MovementType.JUMP -> {
                if (!CollisionCache.isPassable(prev.pos.above(2))) return false

                if (!CollisionCache.isSolid(current.pos.below())) return false
                if (!CollisionCache.isPassable(current.pos.above())) return false
            }

            MovementType.DROP -> {
                if (!CollisionCache.isPassable(prev.pos.above())) return false
                if (!CollisionCache.isSolid(current.pos.below())) return false
            }

            else -> {
                // should be handled by executors
            }
        }
        return true
    }
}