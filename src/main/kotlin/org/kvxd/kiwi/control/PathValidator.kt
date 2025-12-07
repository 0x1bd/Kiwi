package org.kvxd.kiwi.control

import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.NodePath

object PathValidator {

    fun isPathObstructed(path: NodePath): Boolean {
        val limit = (path.index + 5).coerceAtMost(path.size)
        val nodes = path.toList()

        for (i in path.index until limit) {
            val node = nodes[i]

            // might start crouched on a neighboring block and thus is hovering over air
            // so we skip the first node
            if (i == 0) continue

            if (node.type == MovementType.MINE) continue

            if (CollisionCache.isSolid(node.pos) || CollisionCache.isSolid(node.pos.up())) {
                return true
            }

            if (node.type == MovementType.WALK || node.type == MovementType.JUMP) {
                if (!CollisionCache.isSolid(node.pos.down())) {
                    return true
                }
            }
        }

        return false
    }
}