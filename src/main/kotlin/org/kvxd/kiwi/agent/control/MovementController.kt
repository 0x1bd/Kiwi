package org.kvxd.kiwi.agent.control

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils
import kotlin.math.abs

object MovementController {

    private const val Kp = 3.0
    private const val MAX_WALK_SPEED = 0.26
    private const val DEADBAND = 0.015

    fun stop() {
        InputOverride.clearMovement()
    }

    fun alignToBlockCenter(block: BlockPos): Boolean {
        val pos = player.position()
        val relX = pos.x - block.x
        val relZ = pos.z - block.z
        if (relX > 0.3 && relX < 0.7 && relZ > 0.3 && relZ < 0.7) {
            stop()
            return true
        }
        moveToward(Vec3.atBottomCenterOf(block), threshold = 0.05)
        return false
    }

    fun moveToward(targetPos: Vec3, threshold: Double = 0.15) {
        val facingYaw = if (RotationManager.hasTarget) RotationManager.targetYRot else player.yRot
        val delta = targetPos.subtract(player.position())
        val local = RotationUtils.getLocalVector(delta, facingYaw)
        val vel = RotationUtils.getLocalVector(player.deltaMovement, facingYaw)

        InputOverride.update {
            movement(
                forward = computeAxis(local.y.toDouble(), vel.y.toDouble(), threshold),
                back = computeAxis(-local.y.toDouble(), -vel.y.toDouble(), threshold),
                left = computeAxis(local.x.toDouble(), vel.x.toDouble(), threshold),
                right = computeAxis(-local.x.toDouble(), -vel.x.toDouble(), threshold)
            )
        }
    }

    private fun computeAxis(delta: Double, velocity: Double, threshold: Double): Boolean {
        if (abs(delta) < threshold) {
            return abs(velocity) > 0.03
        }
        val desiredSpeed = (delta * Kp).coerceIn(-MAX_WALK_SPEED, MAX_WALK_SPEED)
        val speedError = desiredSpeed - velocity
        return speedError > DEADBAND
    }

    fun shouldSprint(path: NodePath): Boolean {
        if (player.foodData.foodLevel <= 6) return false
        if (player.isUsingItem || player.horizontalCollision) return false

        val current = path.current() ?: return false
        return current.type == MovementType.TRAVEL && path.next()?.type == MovementType.TRAVEL
    }
}