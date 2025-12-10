package org.kvxd.kiwi.control.input

import net.minecraft.client.player.KeyboardInput
import kotlin.properties.Delegates
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

object InputOverride {

    var isActive: Boolean = false
        private set

    class State {
        var forward by flag()
        var back by flag()
        var left by flag()
        var right by flag()
        var jump by flag()

        var sneak by flag {
            //player.crouching = it
        }

        var sprint by flag {
            player.isSprinting = it
        }

        private fun flag(onChange: (Boolean) -> Unit = {}) =
            Delegates.observable(false) { _, _, new ->
                if (isActive) onChange(new)
            }

        fun reset() {
            forward = false
            back = false
            left = false
            right = false
            jump = false
            sneak = false
            sprint = false
        }
    }

    val state = State()

    fun activate() {
        state.reset()
        player.input = KiwiInput()

        isActive = true
    }

    fun deactivate() {
        state.reset()
        player.input = KeyboardInput(client.options)

        isActive = false
    }

    fun reset() = state.reset()
}