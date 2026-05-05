package org.kvxd.kiwi.agent.control.input

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
        val intent = InputOverride.current()
        InputOverride.syncKeysForTick()

        this.keyPresses = Input(
            intent.forward,
            intent.back,
            intent.left,
            intent.right,
            intent.jump,
            intent.sneak,
            intent.sprint
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