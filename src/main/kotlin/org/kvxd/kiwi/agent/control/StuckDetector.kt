package org.kvxd.kiwi.agent.control

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player

class StuckDetector(
    private val distanceSq: Double = 0.0004
) {
    var ticks: Int = 0
        private set

    private var lastPos: Vec3 = Vec3.ZERO

    fun reset() {
        ticks = 0
        lastPos = player.position()
    }

    fun tick(path: NodePath, paused: Boolean): Boolean {
        if (paused) return false
        if (!player.onGround() && !player.isInWater) return false
        val currentNode = path.current() ?: return false
        if (currentNode.type == MovementType.PILLAR || currentNode.type == MovementType.JUMP) return false
        if (InputOverride.isAttacking()) {
            reset()
            return false
        }

        val currentPos = player.position()
        if (currentPos.distanceToSqr(lastPos) < distanceSq) {
            ticks++
        } else {
            ticks = 0
            lastPos = currentPos
        }

        return ticks > ConfigData.stuckThresholdTicks
    }
}