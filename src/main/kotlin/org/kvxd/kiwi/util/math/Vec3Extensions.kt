package org.kvxd.kiwi.util.math

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

fun Vec3.horizontalDistanceTo(other: Vec3): Double {
    val dx = this.x - other.x
    val dz = this.z - other.z
    return sqrt(dx * dx + dz * dz)
}

fun Vec3.horizontalDistanceSqr(other: Vec3): Double {
    val dx = this.x - other.x
    val dz = this.z - other.z
    return dx * dx + dz * dz
}

fun Vec3.toBlockPos(): BlockPos {

    return BlockPos(this.x.toInt(), this.y.toInt(), this.z.toInt())
}