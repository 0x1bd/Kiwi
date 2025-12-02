package org.kvxd.baobab.control

object InputController {

    var active: Boolean = false

    var forward: Boolean = false
    var back: Boolean = false
    var left: Boolean = false
    var right: Boolean = false
    var jump: Boolean = false
    var sneak: Boolean = false
    var sprint: Boolean = false

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