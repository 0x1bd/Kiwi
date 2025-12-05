package org.kvxd.kiwi.control

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.control.movement.ActionResult
import org.kvxd.kiwi.control.movement.actionResult
import org.kvxd.kiwi.control.movement.impl.Input
import org.kvxd.kiwi.control.movement.impl.Rotate
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils
import kotlin.math.abs

object InputHelper {

    fun moveTowardCenter(
        targetPos: Vec3d,
        threshold: Double = 0.05
    ): ActionResult {
        val yaw = if (RotationManager.hasTarget) RotationManager.targetYaw else player.yaw
        val delta = targetPos.subtract(player.entityPos)
        val local = RotationUtils.getLocalVector(delta, yaw)

        val input = resolveInput(
            localX = local.x,
            localY = local.y,
            threshold = threshold,
            velocityX = null,
            velocityY = null
        ) ?: return actionResult()

        return mutableListOf(input)
    }

    fun airStrafe(
        targetPos: Vec3d,
        yaw: Float
    ): ActionResult {
        val delta = targetPos.subtract(player.entityPos)
        val localDistance = RotationUtils.getLocalVector(delta, yaw)
        val localVelocity = RotationUtils.getLocalVector(player.velocity, yaw)

        val input = resolveInput(
            localX = localDistance.x,
            localY = localDistance.y,
            threshold = 0.05,
            velocityX = localVelocity.x,
            velocityY = localVelocity.y
        ) ?: return actionResult()

        return mutableListOf(input, Rotate(yaw))
    }

    private fun resolveInput(
        localX: Float,
        localY: Float,
        threshold: Double,
        velocityX: Float?,
        velocityY: Float?,
        velocityLimit: Double = 0.15
    ): Input? {

        fun press(dist: Float, vel: Float?, sign: Double): Boolean {
            if (abs(dist) <= threshold) return false
            if (dist * sign <= 0) return false
            if (vel != null && vel * sign >= velocityLimit) return false
            return true
        }

        val forward = press(localY, velocityY, +1.0)
        val back = press(localY, velocityY, -1.0)
        val left = press(localX, velocityX, +1.0)
        val right = press(localX, velocityX, -1.0)

        if (!forward && !back && !left && !right) return null

        return Input(forward, back, left, right)
    }

    fun shouldSprint(player: ClientPlayerEntity, path: NodePath): Boolean {
        if (!player.isOnGround && !player.isTouchingWater) return false
        val next = path.peek(1) ?: return false
        return path.current()?.type?.canSprint == true && next.type.canSprint
    }
}
