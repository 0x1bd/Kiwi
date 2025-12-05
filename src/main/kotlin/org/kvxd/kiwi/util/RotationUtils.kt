package org.kvxd.kiwi.util

import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
}