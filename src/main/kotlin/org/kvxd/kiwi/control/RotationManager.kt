package org.kvxd.kiwi.control

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
}