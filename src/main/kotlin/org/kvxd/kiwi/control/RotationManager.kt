package org.kvxd.kiwi.control

import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object RotationManager {

    var targetYRot: Float = 0f
    var targetXRot: Float = 0f
    var hasTarget: Boolean = false

    fun reset() {
        hasTarget = false
    }

    fun setTarget(yaw: Float? = null, pitch: Float? = null) {
        if (yaw != null) {
            targetYRot = RotationUtils.normalize(yaw)
        }

        if (pitch != null) {
            targetXRot = pitch.coerceIn(-90f, 90f)
        }

        hasTarget = true
    }

    fun tick() {
        if (!hasTarget) {
            if (InputOverride.isActive && !ConfigData.freelook) {
                player.xRot = 0f
            }

            return
        }

        if (!ConfigData.freelook) {
            player.yRot = targetYRot
            player.xRot = targetXRot
        }
    }
}