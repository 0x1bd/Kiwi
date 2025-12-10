package org.kvxd.kiwi.control.input

import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

class KiwiInput : ClientInput() {

    private fun getMovementMultiplier(positive: Boolean, negative: Boolean): Float {
        return if (positive == negative) {
            0.0f
        } else {
            if (positive) 1.0f else -1.0f
        }
    }

    override fun tick() {
        this.keyPresses = Input(
            InputOverride.state.forward,
            InputOverride.state.back,
            InputOverride.state.left,
            InputOverride.state.right,
            InputOverride.state.jump,
            InputOverride.state.sneak,
            InputOverride.state.sprint
        )

        val forward = getMovementMultiplier(
            keyPresses.forward,
            keyPresses.backward
        )

        val left = getMovementMultiplier(
            keyPresses.left,
            keyPresses.right
        )

        this.moveVector = Vec2(left, forward).normalized()
    }

}