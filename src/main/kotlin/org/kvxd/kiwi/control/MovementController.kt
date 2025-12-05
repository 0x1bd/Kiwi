package org.kvxd.kiwi.control

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.util.RotationUtils
import kotlin.math.abs

object MovementController {

    fun forward() {
        with(InputOverride.state) {
            forward = true
            back = false
            left = false
            right = false
        }
    }

    fun moveToward(player: ClientPlayerEntity, targetPos: Vec3d, threshold: Double = 0.05) {
        val baseYaw = if (RotationManager.hasTarget) RotationManager.targetYaw else player.yaw
        val delta = targetPos.subtract(player.entityPos)
        val local = RotationUtils.getLocalVector(delta, baseYaw)

        with(InputOverride.state) {
            if (local.y > threshold) forward = true
            else if (local.y < -threshold) back = true

            if (local.x > threshold) left = true
            else if (local.x < -threshold) right = true
        }
    }

    fun applyAirStrafe(player: ClientPlayerEntity, targetPos: Vec3d, yaw: Float) {
        val delta = targetPos.subtract(player.entityPos)
        val localDistance = RotationUtils.getLocalVector(delta, yaw)
        val localVelocity = RotationUtils.getLocalVector(player.velocity, yaw)

        val localForward = localDistance.y
        val localStrafe = localDistance.x
        val velForward = localVelocity.y
        val velStrafe = localVelocity.x

        with(InputOverride.state) {
            if (abs(localForward) > 0.05) {
                if (localForward > 0) {
                    if (velForward < 0.15) forward = true
                } else {
                    if (velForward > -0.15) back = true
                }
            }

            if (abs(localStrafe) > 0.05) {
                if (localStrafe > 0) {
                    if (velStrafe < 0.15) left = true
                } else {
                    if (velStrafe > -0.15) right = true
                }
            }
        }
    }

    fun shouldSprint(player: ClientPlayerEntity, path: NodePath): Boolean {
        val isGrounded = player.isOnGround || player.isTouchingWater
        if (!isGrounded) return false

        val next = path.peek(1) ?: return false
        return path.current()?.type?.canSprint == true && next.type.canSprint
    }
}