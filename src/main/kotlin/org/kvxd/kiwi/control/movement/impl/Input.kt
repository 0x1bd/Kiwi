package org.kvxd.kiwi.control.movement.impl

import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.control.movement.MergeableAction

data class Input(
    val forward: Boolean = false,
    val back: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false
) : MergeableAction {

    override fun execute() {
        InputOverride.state.forward = forward
        InputOverride.state.back = back
        InputOverride.state.left = left
        InputOverride.state.right = right
        InputOverride.state.jump = jump
        InputOverride.state.sneak = sneak
        InputOverride.state.sprint = sprint
    }

    override fun merge(other: MergeableAction): MergeableAction? {
        if (other !is Input) return null

        return Input(
            forward = this.forward || other.forward,
            back = this.back || other.back,
            left = this.left || other.left,
            right = this.right || other.right,
            sprint = this.sprint || other.sprint,
            jump = this.jump || other.jump,
            sneak = this.sneak || other.sneak
        )
    }
}