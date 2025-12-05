package org.kvxd.kiwi.control

import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.player

object RotationManager {

    var targetYaw: Float = 0f
    var targetPitch: Float = 0f
    var hasTarget: Boolean = false

    fun reset() {
        hasTarget = false
    }

    fun setTarget(yaw: Float, pitch: Float) {
        targetYaw = yaw
        targetPitch = pitch
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