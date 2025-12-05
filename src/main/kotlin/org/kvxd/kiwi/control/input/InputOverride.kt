package org.kvxd.kiwi.control.input

import kotlin.properties.Delegates
import net.minecraft.client.input.KeyboardInput
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
            player.isSneaking = it
        }

        var sprint by flag {
            player.isSprinting = it
        }

        // TODO: Implement proper override
        var attack by flag {
            client.options.attackKey.isPressed = it
        }

        // TODO: Implement proper override
        var use by flag {
            client.options.useKey.isPressed = it
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
            attack = false
            use = false
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