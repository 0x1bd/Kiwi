package org.kvxd.kiwi.util

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
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

    fun getLookYaw(start: Vec3d, target: Vec3d): Float {
        val dx = target.x - start.x
        val dz = target.z - start.z

        return MathHelper.wrapDegrees(
            ((atan2(dz, dx) * 180.0 / PI).toFloat() - 90f)
        )
    }

    fun getHorizontalDistanceSqr(v1: Vec3d, v2: Vec3d): Double {
        val dx = v1.x - v2.x
        val dz = v1.z - v2.z

        return dx * dx + dz * dz
    }

    fun getLocalVector(globalDelta: Vec3d, yawDegrees: Float): Vec2f {
        val yawRad = Math.toRadians(yawDegrees.toDouble())
        val s = sin(yawRad)
        val c = cos(yawRad)

        val localForward = globalDelta.x * (-s) + globalDelta.z * c
        val localStrafe = globalDelta.x * c - globalDelta.z * (-s)

        return Vec2f(localStrafe.toFloat(), localForward.toFloat())
    }

    fun normalize(angle: Float): Float {
        var a = angle % 360f
        if (a >= 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    fun getLookRotations(target: Vec3d): Vec2f {
        val eyePos = player.eyePos

        val dx = target.x - eyePos.x
        val dy = target.y - eyePos.y
        val dz = target.z - eyePos.z

        val distXZ = sqrt(dx * dx + dz * dz)

        val yaw = MathHelper.wrapDegrees((atan2(dz, dx) * 180.0 / PI).toFloat() - 90f)
        val pitch = MathHelper.wrapDegrees((-atan2(dy, distXZ) * 180.0 / PI).toFloat())

        return Vec2f(yaw, pitch)
    }

    fun isLookingAt(target: Vec3d, threshold: Double): Boolean {
        val yaw = if (ConfigData.freelook)
            RotationManager.targetYaw else player.yaw

        val pitch = if (ConfigData.freelook)
            RotationManager.targetPitch else player.pitch

        val desired = getLookRotations(target)
        val yawDiff = abs(normalize(desired.x - yaw))
        val pitchDiff = abs(normalize(desired.y - pitch))

        return yawDiff < threshold && pitchDiff < threshold
    }

    fun getDirection(blockPos: BlockPos): Direction {
        val eye = player.eyePos
        val center = Vec3d.ofCenter(blockPos)

        val look = Vec3d(
            center.x - eye.x,
            center.y - eye.y,
            center.z - eye.z
        )

        val absX = abs(look.x)
        val absY = abs(look.y)
        val absZ = abs(look.z)

        return when {
            absY >= absX && absY >= absZ ->
                if (look.y > 0) Direction.UP else Direction.DOWN

            absX >= absZ ->
                if (look.x > 0) Direction.EAST else Direction.WEST

            else ->
                if (look.z > 0) Direction.SOUTH else Direction.NORTH
        }
    }
}