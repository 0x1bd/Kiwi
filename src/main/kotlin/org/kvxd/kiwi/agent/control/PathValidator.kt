package org.kvxd.kiwi.agent.control

import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.LineOfSight
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import kotlin.math.min

object PathValidator {

    data class ValidationResult(
        val obstructed: Boolean,
        val reason: String = ""
    )

    fun isPathObstructed(path: NodePath): Boolean = validate(path).obstructed

    fun validate(path: NodePath): ValidationResult {
        if (path.isEmpty || path.isFinished) return ValidationResult(false)

        val current = path.current() ?: return ValidationResult(false)
        val prev = path.previous()


        if (path.index > 0) {
            invalidNodeReason(current)?.let {
                return ValidationResult(true, "current node invalid: $it")
            }
        }

        if (prev != null) {
            invalidTransitionReason(prev, current)?.let {
                return ValidationResult(true, "current transition invalid: $it")
            }
        }

        val lookahead = min(path.size, path.index + 3)
        var previousNodeForLookahead = current

        for (i in (path.index + 1) until lookahead) {
            val nextNode = path[i] ?: continue


            invalidNodeReason(nextNode)?.let {
                return ValidationResult(true, "lookahead[$i] node invalid: $it")
            }
            invalidTransitionReason(previousNodeForLookahead, nextNode)?.let {
                return ValidationResult(true, "lookahead[$i] transition invalid: $it")
            }

            previousNodeForLookahead = nextNode
        }

        return ValidationResult(false)
    }

    private fun isClearable(blockPos: net.minecraft.core.BlockPos): Boolean {
        if (org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isPassable(blockPos)) return true
        if (org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isDangerous(blockPos)) return false
        val cost = org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil.calculateMiningCost(listOf(blockPos))
        return cost != null
    }

    private fun invalidNodeReason(node: Node): String? {
        if (node.type == MovementType.TRAVEL || node.type == MovementType.JUMP || node.type == MovementType.DROP) {
            if (!isClearable(node.pos)) return "${node.pos} feet not clearable"
            if (!isClearable(node.pos.above())) return "${node.pos.above()} head not clearable"
        }
        return null
    }

    private fun invalidTransitionReason(prev: Node, current: Node): String? {
        when (current.type) {
            MovementType.TRAVEL -> {
                if (prev.pos.distSqr(current.pos) > 3.0) {
                    if (!LineOfSight.check(prev, current)) return "${prev.pos} -> ${current.pos} line of sight blocked"
                }
            }

            MovementType.JUMP -> {
                if (!isClearable(prev.pos.above(2))) return "${prev.pos.above(2)} jump headroom not clearable"

                if (!org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isSolid(current.pos.below())) return "${current.pos.below()} jump landing support not solid"
            }

            MovementType.DROP -> {
                if (!isClearable(prev.pos.above())) return "${prev.pos.above()} drop headroom not clearable"
                if (!org.kvxd.kiwi.agent.pathing.cache.CollisionCache.isSolid(current.pos.below())) return "${current.pos.below()} drop landing support not solid"
            }

            else -> {
                // should be handled by executors
            }
        }
        return null
    }
}
