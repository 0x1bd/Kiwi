package org.kvxd.kiwi.control.input

import net.minecraft.client.input.KeyboardInput
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

object InputOverride {

    @JvmStatic
    var isActive: Boolean = false

    @JvmStatic
    var state: State = State()

    class State {

        var forward: Boolean = false
        var back: Boolean = false
        var left: Boolean = false
        var right: Boolean = false
        var jump: Boolean = false
        var sneak: Boolean = false
            set(value) {
                field = value
                if (isActive)
                    player.isSneaking = value
            }

        var sprint: Boolean = false
            set(value) {
                field = value
                if (isActive)
                    player.isSprinting = value
            }
    }

    fun activate() {
        reset()

        player.input = KiwiInput()

        isActive = true
    }

    fun deactivate() {
        reset()

        player.input = KeyboardInput(client.options)
    }

    fun reset() {
        state.forward = false
        state.back = false
        state.left = false
        state.right = false
        state.jump = false
        state.sneak = false
        state.sprint = false
    }
}