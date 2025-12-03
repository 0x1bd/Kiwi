package org.kvxd.kiwi.control

import org.kvxd.kiwi.client

object InputController {

    var active: Boolean = false

    var forward: Boolean = false
    var back: Boolean = false
    var left: Boolean = false
    var right: Boolean = false

    var jump: Boolean = false

    var sneak: Boolean = false
        set(value) {
            field = value
            if (active) client.player?.isSneaking = value
        }

    var sprint: Boolean = false
        set(value) {
            field = value
            if (active) client.player?.isSprinting = value
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