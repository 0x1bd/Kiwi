package org.kvxd.kiwi.util.math

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.player
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RotationUtils {

    private const val RAD_TO_DEG = 180.0 / PI
    private const val DEG_TO_RAD = PI / 180.0

    private fun calcYaw(dx: Double, dz: Double): Float {
        return Mth.wrapDegrees((atan2(dz, dx) * RAD_TO_DEG).toFloat() - 90f)
    }

    fun getLookYaw(start: Vec3, target: Vec3): Float {
        return calcYaw(target.x - start.x, target.z - start.z)
    }

    fun getRotationVector(xRot: Float, yRot: Float): Vec3 {
        val pitch = xRot * DEG_TO_RAD
        val yaw = -yRot * DEG_TO_RAD

        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        return Vec3(
            (sinYaw * cosPitch),
            (-sinPitch),
            (cosYaw * cosPitch)
        )
    }

    fun getLocalVector(globalDelta: Vec3, yRotDegrees: Float): Vec2 {
        val yaw = Math.toRadians(yRotDegrees.toDouble())
        val s = sin(yaw)
        val c = cos(yaw)

        val forward = globalDelta.x * (-s) + globalDelta.z *  c
        val strafe  = globalDelta.x *  c - globalDelta.z * (-s)

        return Vec2(strafe.toFloat(), forward.toFloat())
    }

    fun normalize(angle: Float): Float {
        var a = angle % 360f
        if (a >= 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    fun getLookRotations(target: Vec3): Vec2 {
        val eye = player.eyePosition

        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z

        val distXZ = sqrt(dx * dx + dz * dz)

        val yaw = calcYaw(dx, dz)
        val pitch = Mth.wrapDegrees((-atan2(dy, distXZ) * RAD_TO_DEG).toFloat())

        return Vec2(yaw, pitch)
    }

    fun isLookingAt(target: Vec3, threshold: Double): Boolean {
        val yRot = if (ConfigData.freelook) RotationManager.targetYRot else player.yRot
        val xRot = if (ConfigData.freelook) RotationManager.targetXRot else player.xRot

        val desired = getLookRotations(target)

        val yawDiff = abs(normalize(desired.x - yRot))
        val pitchDiff = abs(normalize(desired.y - xRot))

        return yawDiff < threshold && pitchDiff < threshold
    }

    fun getDirection(blockPos: BlockPos): Direction {
        val eye = player.eyePosition
        val center = Vec3.atCenterOf(blockPos)

        val dx = center.x - eye.x
        val dy = center.y - eye.y
        val dz = center.z - eye.z

        val absX = abs(dx)
        val absY = abs(dy)
        val absZ = abs(dz)

        return when {
            absY >= absX && absY >= absZ -> if (dy > 0) Direction.UP else Direction.DOWN
            absX >= absZ -> if (dx > 0) Direction.EAST else Direction.WEST
            else -> if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }
}