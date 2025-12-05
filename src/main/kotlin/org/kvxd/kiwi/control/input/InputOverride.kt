package org.kvxd.kiwi.control.input

import net.minecraft.client.input.KeyboardInput
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

object InputOverride {

    @JvmStatic
    var isActive: Boolean = false

    @JvmStatic
    var state: State = State()

    data class State(
        var forward: Boolean = false,
        var back: Boolean = false,
        var left: Boolean = false,
        var right: Boolean = false,
        var jump: Boolean = false,
        var sneak: Boolean = false,
        var sprint: Boolean = false
    )

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
        state = State()
    }
}