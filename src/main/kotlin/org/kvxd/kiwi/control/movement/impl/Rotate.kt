package org.kvxd.kiwi.control.movement.impl

import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.movement.MergeableAction

data class Rotate(
    val yaw: Float? = null,
    val pitch: Float? = null
) : MergeableAction {

    override fun execute() {
        RotationManager.setTarget(yaw, pitch)
    }

    override fun merge(other: MergeableAction): MergeableAction? {
        if (other !is Rotate) return null

        return Rotate(
            yaw = other.yaw ?: this.yaw,
            pitch = other.pitch ?: this.pitch
        )
    }
}