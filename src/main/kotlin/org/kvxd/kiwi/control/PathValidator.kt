package org.kvxd.kiwi.control

import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.move.Physics
import kotlin.math.min

object PathValidator {

    fun isPathObstructed(path: NodePath, lookaheadCount: Int = 5): Boolean {
        val nodes = path.toList()
        val currentIndex = path.index

        val end = min(nodes.size, currentIndex + lookaheadCount)

        for (i in currentIndex until end) {
            val node = nodes[i]

            if (!Physics.isWalkable(node.pos)) {
                return true
            }

            if (node.type == MovementType.JUMP) {
                if (Physics.isSolid(node.pos.up(2))) return true
            }
        }

        return false
    }
}