package org.kvxd.kiwi.control

import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object RotationManager {

    var targetYaw: Float = 0f
    var targetPitch: Float = 0f
    var hasTarget: Boolean = false

    fun reset() {
        hasTarget = false
    }

    fun setTarget(yaw: Float? = null, pitch: Float? = null) {
        if (yaw != null) {
            targetYaw = RotationUtils.normalize(yaw)
        }

        if (pitch != null) {
            targetPitch = pitch.coerceIn(-90f, 90f)
        }

        hasTarget = true
    }

    fun tick() {
        if (!hasTarget) return

        if (!ConfigManager.data.freelook) {
            player.yaw = targetYaw
            player.pitch = targetPitch
        }
    }
}