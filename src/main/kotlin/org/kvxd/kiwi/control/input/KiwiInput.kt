package org.kvxd.kiwi.control.input

import net.minecraft.client.input.Input
import net.minecraft.util.PlayerInput
import net.minecraft.util.math.Vec2f

class KiwiInput : Input() {

    private fun getMovementMultiplier(positive: Boolean, negative: Boolean): Float {
        return if (positive == negative) {
            0.0f
        } else {
            if (positive) 1.0f else -1.0f
        }
    }

    override fun tick() {
        this.playerInput = PlayerInput(
            InputOverride.state.forward,
            InputOverride.state.back,
            InputOverride.state.left,
            InputOverride.state.right,
            InputOverride.state.jump,
            InputOverride.state.sneak,
            InputOverride.state.sprint
        )

        val forward = getMovementMultiplier(
            playerInput.forward,
            playerInput.backward
        )

        val left = getMovementMultiplier(
            playerInput.left,
            playerInput.right
        )

        this.movementVector = Vec2f(left, forward).normalize()
    }

}