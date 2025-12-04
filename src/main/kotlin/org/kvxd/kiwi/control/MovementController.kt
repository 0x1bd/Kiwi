package org.kvxd.kiwi.control

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.util.RotationUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object MovementController {

    fun applyControls(targetYaw: Float, currentYaw: Float) {
        if (ConfigManager.data.freelook) {
            val diff = MathHelper.wrapDegrees(targetYaw - currentYaw)
            val rad = Math.toRadians(diff.toDouble())

            val forward = cos(rad)
            val strafe = sin(rad)

            InputController.forward = forward > 0.3
            InputController.back = forward < -0.3
            InputController.right = strafe > 0.3
            InputController.left = strafe < -0.3
        } else {
            InputController.forward = true
        }
    }

    fun applyAirStrafe(player: ClientPlayerEntity, targetPos: Vec3d) {
        val delta = targetPos.subtract(player.entityPos)

        val localDistance = RotationUtils.getLocalVector(delta, player.yaw)
        val localVelocity = RotationUtils.getLocalVector(player.velocity, player.yaw)

        val localForward = localDistance.y
        val localStrafe = localDistance.x
        val velForward = localVelocity.y
        val velStrafe = localVelocity.x

        if (abs(localForward) > 0.05) {
            if (localForward > 0) {
                if (velForward < 0.15) InputController.forward = true
            } else {
                if (velForward > -0.15) InputController.back = true
            }
        }

        if (abs(localStrafe) > 0.05) {
            if (localStrafe > 0) {
                if (velStrafe < 0.15) InputController.left = true
            } else {
                if (velStrafe > -0.15) InputController.right = true
            }
        }
    }

    fun shouldSprint(player: ClientPlayerEntity, path: NodePath): Boolean {
        val isGrounded = player.isOnGround || player.isTouchingWater
        if (!isGrounded) return false

        val next = path.peek(1) ?: return false
        return next.type.canSprint
    }

}