package org.kvxd.kiwi.control

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.util.RotationUtils

object MovementController {

    fun stop() {
        with(InputOverride.state) {
            forward = false; back = false; left = false; right = false; jump = false; sprint = false
        }
    }

    fun forward() {
        with(InputOverride.state) {
            forward = true; back = false; left = false; right = false
        }
    }

    fun moveToward(player: ClientPlayerEntity, targetPos: Vec3d, threshold: Double = 0.15) {
        val facingYaw = if (RotationManager.hasTarget) RotationManager.targetYaw else player.yaw

        val delta = targetPos.subtract(player.entityPos)
        val local = RotationUtils.getLocalVector(delta, facingYaw)

        val vel = RotationUtils.getLocalVector(player.velocity, facingYaw)

        with(InputOverride.state) {
            if (local.y > threshold) {
                if (vel.y < 0.25 || local.y > 0.5) forward = true
            } else if (local.y < -threshold) {
                if (vel.y > -0.25 || local.y < -0.5) back = true
            }

            if (local.x > threshold) {
                if (vel.x < 0.25 || local.x > 0.5) left = true
            } else if (local.x < -threshold) {
                if (vel.x > -0.25 || local.x < -0.5) right = true
            }
        }
    }

    fun shouldSprint(player: ClientPlayerEntity, path: NodePath): Boolean {
        if (player.hungerManager.foodLevel <= 6) return false
        if (player.isUsingItem) return false
        if (player.horizontalCollision) return false

        val current = path.current() ?: return false

        if (current.type == MovementType.TRAVEL && path.next()?.type == MovementType.TRAVEL) return true

        val dist = player.blockPos.getSquaredDistance(current.pos)

        return current.type.canSprint && dist > 6.0
    }
}