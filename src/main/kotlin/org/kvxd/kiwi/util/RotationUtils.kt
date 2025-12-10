package org.kvxd.kiwi.util

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

    fun getLookYaw(start: Vec3, target: Vec3): Float {
        val dx = target.x - start.x
        val dz = target.z - start.z

        return Mth.wrapDegrees(
            ((atan2(dz, dx) * 180.0 / PI).toFloat() - 90f)
        )
    }

    fun getHorizontalDistanceSqr(v1: Vec3, v2: Vec3): Double {
        val dx = v1.x - v2.x
        val dz = v1.z - v2.z

        return dx * dx + dz * dz
    }

    fun getLocalVector(globalDelta: Vec3, yRotDegrees: Float): Vec2 {
        val yawRad = Math.toRadians(yRotDegrees.toDouble())
        val s = sin(yawRad)
        val c = cos(yawRad)

        val localForward = globalDelta.x * (-s) + globalDelta.z * c
        val localStrafe = globalDelta.x * c - globalDelta.z * (-s)

        return Vec2(localStrafe.toFloat(), localForward.toFloat())
    }

    fun normalize(angle: Float): Float {
        var a = angle % 360f
        if (a >= 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    fun getLookRotations(target: Vec3): Vec2 {
        val eyePos = player.eyePosition

        val dx = target.x - eyePos.x
        val dy = target.y - eyePos.y
        val dz = target.z - eyePos.z

        val distXZ = sqrt(dx * dx + dz * dz)

        val yaw = Mth.wrapDegrees((atan2(dz, dx) * 180.0 / PI).toFloat() - 90f)
        val pitch = Mth.wrapDegrees((-atan2(dy, distXZ) * 180.0 / PI).toFloat())

        return Vec2(yaw, pitch)
    }

    fun isLookingAt(target: Vec3, threshold: Double): Boolean {
        val yRot = if (ConfigData.freelook)
            RotationManager.targetYRot else player.yRot

        val xRot = if (ConfigData.freelook)
            RotationManager.targetXRot else player.xRot

        val desired = getLookRotations(target)
        val yawDiff = abs(normalize(desired.x - yRot))
        val pitchDiff = abs(normalize(desired.y - xRot))

        return yawDiff < threshold && pitchDiff < threshold
    }

    fun getDirection(blockPos: BlockPos): Direction {
        val eye = player.eyePosition
        val center = Vec3.atCenterOf(blockPos)

        val look = Vec3(
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