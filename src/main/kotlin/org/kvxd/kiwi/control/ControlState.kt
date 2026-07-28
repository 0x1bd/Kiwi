package org.kvxd.kiwi.control

data class ControlState(
    val forward: Boolean = false,
    val back: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false
) {
    companion object {
        val NEUTRAL = ControlState()
    }
}

class ControlStateBuilder {
    var forward = false
    var back = false
    var left = false
    var right = false
    var jump = false
    var sneak = false
    var sprint = false

    fun clearMovement() {
        forward = false
        back = false
        left = false
        right = false
        sprint = false
    }

    fun build(): ControlState = ControlState(forward, back, left, right, jump, sneak, sprint)
}
