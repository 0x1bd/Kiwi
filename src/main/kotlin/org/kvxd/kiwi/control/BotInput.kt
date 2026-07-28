package org.kvxd.kiwi.control

import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

class BotInput : ClientInput() {

    override fun tick() {
        val state = Controller.currentState()

        keyPresses = Input(
            state.forward,
            state.back,
            state.left,
            state.right,
            state.jump,
            state.sneak,
            state.sprint
        )

        val forward = axis(state.forward, state.back)
        val left = axis(state.left, state.right)
        moveVector = Vec2(left, forward).normalized()
    }

    private fun axis(positive: Boolean, negative: Boolean): Float = when {
        positive == negative -> 0f
        positive -> 1f
        else -> -1f
    }
}
