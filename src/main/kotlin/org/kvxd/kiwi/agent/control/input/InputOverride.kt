package org.kvxd.kiwi.agent.control.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.KeyboardInput
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

data class InputIntent(
    val forward: Boolean = false,
    val back: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false,
    val attack: Boolean = false,
    val use: Boolean = false
)

class InputIntentBuilder internal constructor(base: InputIntent) {
    var forward = base.forward
    var back = base.back
    var left = base.left
    var right = base.right
    var jump = base.jump
    var sneak = base.sneak
    var sprint = base.sprint
    var attack = base.attack
    var use = base.use

    fun movement(
        forward: Boolean = false,
        back: Boolean = false,
        left: Boolean = false,
        right: Boolean = false,
        jump: Boolean = false,
        sneak: Boolean = false,
        sprint: Boolean = false
    ) {
        this.forward = forward
        this.back = back
        this.left = left
        this.right = right
        this.jump = jump
        this.sneak = sneak
        this.sprint = sprint
    }

    fun clearMovement() {
        movement()
    }

    fun clearActions() {
        attack = false
        use = false
    }

    internal fun build(): InputIntent = InputIntent(
        forward = forward,
        back = back,
        left = left,
        right = right,
        jump = jump,
        sneak = sneak,
        sprint = sprint,
        attack = attack,
        use = use
    )
}

object InputOverride {

    private val neutral = InputIntent()
    private val attackKey = InputConstants.Type.MOUSE.getOrCreate(0)
    private val useKey = InputConstants.Type.MOUSE.getOrCreate(1)

    private var intent = neutral
    private var syncedAttack = false
    private var syncedUse = false

    var isActive: Boolean = false
        private set

    fun capture() {
        if (isActive) return
        intent = neutral
        syncedAttack = false
        syncedUse = false
        player.input = KiwiInput()
        isActive = true
    }

    fun release() {
        publish(neutral)
        syncKeys(neutral)
        player.isSprinting = false
        isActive = false
        player.input = KeyboardInput(client.options)
    }

    fun update(block: InputIntentBuilder.() -> Unit) {
        capture()
        val builder = InputIntentBuilder(intent)
        builder.block()
        publish(builder.build())
    }

    fun clearMovement() {
        if (!isActive) return
        update { clearMovement() }
    }

    fun clearActions() {
        if (!isActive) return
        update { clearActions() }
    }

    fun clearAll() {
        if (!isActive) return
        publish(neutral)
        player.isSprinting = false
    }

    fun current(): InputIntent = if (isActive) intent else neutral

    fun isAttacking(): Boolean = current().attack

    internal fun syncKeysForTick() {
        syncKeys(current())
    }

    private fun publish(next: InputIntent) {
        intent = next
        player.isSprinting = next.sprint
    }

    private fun syncKeys(next: InputIntent) {
        syncKey(attackKey, syncedAttack, next.attack)
        syncKey(useKey, syncedUse, next.use)
        syncedAttack = next.attack
        syncedUse = next.use
    }

    private fun syncKey(key: InputConstants.Key, previous: Boolean, pressed: Boolean) {
        KeyMapping.set(key, pressed)
        if (pressed && !previous) {
            KeyMapping.click(key)
        }
    }
}
