package org.kvxd.kiwi.control

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils
import kotlin.math.abs

object MovementController {

    fun stop() {
        with(InputOverride.state) {
            forward = false
            back = false
            left = false
            right = false
            jump = false
            sprint = false
        }
    }

    fun alignToBlockCenter(block: BlockPos): Boolean {
        val pos = player.position()

        val relX = pos.x - block.x
        val relZ = pos.z - block.z

        val alignedX = relX > 0.3 && relX < 0.7
        val alignedZ = relZ > 0.3 && relZ < 0.7

        if (alignedX && alignedZ) {
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

        with(InputOverride.state) {
            forward = false; back = false; left = false; right = false

            if (abs(local.x) > threshold) {
                if (local.x > 0) {
                    if (vel.x < 0.25) left = true
                } else {
                    if (vel.x > -0.25) right = true
                }
            }

            if (abs(local.y) > threshold) {
                if (local.y > 0) {
                    if (vel.y < 0.25) forward = true
                } else {
                    if (vel.y > -0.25) back = true
                }
            }

            if (abs(local.x) < 0.2 && abs(vel.x) > 0.1) {
                left = false; right = false
            }
            if (abs(local.y) < 0.2 && abs(vel.y) > 0.1) {
                forward = false; back = false
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

        return current.type.canSprint && dist > 5.0
    }
}