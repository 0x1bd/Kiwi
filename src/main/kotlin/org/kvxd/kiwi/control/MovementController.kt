package org.kvxd.kiwi.control

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.player
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

    fun moveToward(targetPos: Vec3, threshold: Double = 0.15) {
        val facingYaw = if (RotationManager.hasTarget) RotationManager.targetYRot else player.yRot

        val delta = targetPos.subtract(player.position())
        val local = RotationUtils.getLocalVector(delta, facingYaw)

        val vel = RotationUtils.getLocalVector(player.deltaMovement, facingYaw)

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

    fun shouldSprint(path: NodePath): Boolean {
        if (player.foodData.foodLevel <= 6) return false
        if (player.isUsingItem) return false
        if (player.horizontalCollision) return false

        val current = path.current() ?: return false

        if (current.type == MovementType.TRAVEL && path.next()?.type == MovementType.TRAVEL) return true

        val dist = player.blockPosition().distSqr(current.pos)

        return current.type.canSprint && dist > 6.0
    }
}