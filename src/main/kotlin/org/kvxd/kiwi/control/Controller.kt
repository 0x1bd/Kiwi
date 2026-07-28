package org.kvxd.kiwi.control

import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Controller {

    private const val AXIS_DEADZONE = 0.12

    var isEngaged: Boolean = false
    private set

    private var published: ControlState = ControlState.NEUTRAL
    private var pending = ControlStateBuilder()

    fun engage() {
        if (isEngaged) return
        pending = ControlStateBuilder()
        published = ControlState.NEUTRAL
        player.input = BotInput()
        isEngaged = true
    }

    fun release() {
        if (!isEngaged) {
            LookController.reset()
            return
        }
        published = ControlState.NEUTRAL
        pending = ControlStateBuilder()
        player.isSprinting = false
        player.input = KeyboardInput(client.options)
        isEngaged = false
        BlockBreaker.stop()
        LookController.reset()
    }

    fun currentState(): ControlState = if (isEngaged) published else ControlState.NEUTRAL

    fun beginTick() {
        pending = ControlStateBuilder()
    }

    fun endTick() {
        published = pending.build()
        if (isEngaged) player.isSprinting = published.sprint
    }

    fun input(): ControlStateBuilder = pending

    fun stopMoving() {
        pending.clearMovement()
    }

    fun moveTowards(direction: Vec3, sprint: Boolean = false) {
        val yaw = LookController.effectiveYaw()
        val radians = yaw * PI / 180.0
        val s = sin(radians)
        val c = cos(radians)

        val forward = direction.z * c - direction.x * s
        val strafe = direction.x * c + direction.z * s

        val builder = pending
        builder.forward = forward > AXIS_DEADZONE
        builder.back = forward < -AXIS_DEADZONE
        builder.left = strafe > AXIS_DEADZONE
        builder.right = strafe < -AXIS_DEADZONE
        builder.sprint = sprint && builder.forward && !builder.back
    }
}
