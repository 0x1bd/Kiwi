package org.kvxd.kiwi.control

import net.minecraft.world.entity.ai.attributes.Attributes
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import kotlin.math.sqrt

class MotionState(
    @JvmField val x: Double,
    @JvmField val z: Double,
    @JvmField val vx: Double,
    @JvmField val vz: Double
) {
    val speed: Double get() = sqrt(vx * vx + vz * vz)
}

class MotionParameters(
    val friction: Double,
    val acceleration: Double
) {
    val terminalSpeed: Double
        get() = if (friction >= 1.0) Double.MAX_VALUE else acceleration / (1.0 - friction)

    fun stoppingDistance(speed: Double): Double =
        if (friction >= 1.0) Double.MAX_VALUE else speed * friction / (1.0 - friction)
}

object MotionModel {

    const val AIR_FRICTION = 0.91
    const val GROUND_SPEED_CONSTANT = 0.21600002
    const val AIR_ACCELERATION = 0.02
    const val SPRINT_MULTIPLIER = 1.3

    fun sample(sprinting: Boolean): MotionParameters {
        val onGround = player.onGround()
        val blockFriction = runCatching {
            level.getBlockState(player.blockPosBelowThatAffectsMyMovement).block.friction.toDouble()
        }.getOrDefault(0.6)

        val friction = if (onGround) blockFriction * AIR_FRICTION else AIR_FRICTION
        val walkSpeed = runCatching { player.getAttributeValue(Attributes.MOVEMENT_SPEED) }.getOrDefault(0.1)
        val speed = walkSpeed * if (sprinting) SPRINT_MULTIPLIER else 1.0

        val acceleration = if (onGround) {
            speed * (GROUND_SPEED_CONSTANT / (friction * friction * friction))
        } else {
            AIR_ACCELERATION
        }

        return MotionParameters(friction, acceleration)
    }

    fun current(): MotionState =
        MotionState(player.x, player.z, player.deltaMovement.x, player.deltaMovement.z)

    fun step(state: MotionState, dirX: Double, dirZ: Double, parameters: MotionParameters): MotionState {
        val vx = state.vx + dirX * parameters.acceleration
        val vz = state.vz + dirZ * parameters.acceleration
        return MotionState(state.x + vx, state.z + vz, vx * parameters.friction, vz * parameters.friction)
    }

    fun rollout(
        state: MotionState,
        dirX: Double,
        dirZ: Double,
        parameters: MotionParameters,
        ticks: Int
    ): MotionState {
        var current = state
        for (tick in 0 until ticks) current = step(current, dirX, dirZ, parameters)
        return current
    }
}
