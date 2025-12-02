package org.kvxd.baobab.control

import org.kvxd.baobab.player

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
            player.isSneaking = value
        }
    var sprint: Boolean = false
        set(value) {
            field = value
            player.isSprinting = value
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