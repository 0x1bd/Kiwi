package org.kvxd.kiwi.control

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object LookController {

    private const val RAD_TO_DEG = 180.0 / Math.PI

    @JvmStatic
    var hasTarget: Boolean = false
    private set

    @JvmStatic
    var targetYaw: Float = 0f
    private set

    @JvmStatic
    var targetPitch: Float = 0f
    private set

    fun reset() {
        hasTarget = false
    }

    fun set(yaw: Float? = null, pitch: Float? = null) {
        if (yaw != null) targetYaw = Mth.wrapDegrees(yaw)
        if (pitch != null) targetPitch = pitch.coerceIn(-90f, 90f)
        hasTarget = true
    }

    fun lookAt(target: Vec3) {
        val eye = player.eyePosition
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val horizontal = sqrt(dx * dx + dz * dz)
        set(
            yaw = Mth.wrapDegrees((atan2(dz, dx) * RAD_TO_DEG).toFloat() - 90f),
            pitch = Mth.wrapDegrees((-atan2(dy, horizontal) * RAD_TO_DEG).toFloat())
        )
    }

    fun faceHorizontally(from: Vec3, to: Vec3) {
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (dx * dx + dz * dz < 1.0E-6) return
        set(yaw = Mth.wrapDegrees((atan2(dz, dx) * RAD_TO_DEG).toFloat() - 90f))
    }

    fun effectiveYaw(): Float = if (hasTarget && ConfigData.freelook) targetYaw else player.yRot

    fun effectivePitch(): Float = if (hasTarget && ConfigData.freelook) targetPitch else player.xRot

    fun isAimedAt(target: Vec3, toleranceDegrees: Double = 1.5): Boolean {
        if (!hasTarget) return false
        val eye = player.eyePosition
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val desiredYaw = Mth.wrapDegrees((atan2(dz, dx) * RAD_TO_DEG).toFloat() - 90f)
        val desiredPitch = Mth.wrapDegrees((-atan2(dy, horizontal) * RAD_TO_DEG).toFloat())
        return abs(Mth.wrapDegrees(desiredYaw - effectiveYaw())) < toleranceDegrees &&
            abs(Mth.wrapDegrees(desiredPitch - effectivePitch())) < toleranceDegrees
    }

    fun tick() {
        if (!hasTarget) return
        if (!ConfigData.freelook) {
            player.yRot = targetYaw
            player.xRot = targetPitch
        }
    }

    fun rotationVector(pitch: Float, yaw: Float): Vec3 {
        val pitchRad = pitch * Math.PI / 180.0
        val yawRad = -yaw * Math.PI / 180.0
        return Vec3(
            kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad),
            -kotlin.math.sin(pitchRad),
            kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)
        )
    }
}
