package org.kvxd.kiwi.control

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.util.RotationUtils
import kotlin.math.abs

object MovementController {

    fun applyControls() {
        InputController.forward = true
        InputController.back = false
        InputController.left = false
        InputController.right = false
    }

    fun moveToward(player: ClientPlayerEntity, targetPos: Vec3d, threshold: Double = 0.05) {
        val baseYaw = if (RotationManager.hasTarget) RotationManager.targetYaw else player.yaw
        val delta = targetPos.subtract(player.entityPos)
        val local = RotationUtils.getLocalVector(delta, baseYaw)

        if (local.y > threshold) InputController.forward = true
        else if (local.y < -threshold) InputController.back = true

        if (local.x > threshold) InputController.left = true
        else if (local.x < -threshold) InputController.right = true
    }

    fun applyAirStrafe(player: ClientPlayerEntity, targetPos: Vec3d, yaw: Float) {
        val delta = targetPos.subtract(player.entityPos)
        val localDistance = RotationUtils.getLocalVector(delta, yaw)
        val localVelocity = RotationUtils.getLocalVector(player.velocity, yaw)

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