package org.kvxd.kiwi.agent.control

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.player
import kotlin.math.abs

object PathProgress {

    private const val DEFAULT_NODE_RADIUS_SQ = 0.42 * 0.42
    private const val FINAL_NODE_RADIUS_SQ = 0.22 * 0.22
    private const val DROP_NODE_RADIUS_SQ = 0.55 * 0.55

    fun hasReachedCurrent(path: NodePath): Boolean {
        val node = path.current() ?: return false
        val radiusSq = when {
            path.next() == null -> FINAL_NODE_RADIUS_SQ
            node.type == MovementType.DROP -> DROP_NODE_RADIUS_SQ
            else -> DEFAULT_NODE_RADIUS_SQ
        }

        if (horizontalDistanceSq(player.position(), node.toVec()) > radiusSq) return false
        return hasReachedNodeHeight(node)
    }

    private fun horizontalDistanceSq(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun hasReachedNodeHeight(node: Node): Boolean {
        return when (node.type) {
            MovementType.JUMP -> player.y >= node.pos.y - 0.25
            MovementType.DROP -> player.y <= node.pos.y + 0.35
            else -> abs(player.y - node.pos.y) <= 0.85
        }
    }
}
