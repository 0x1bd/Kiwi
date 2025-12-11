package org.kvxd.kiwi.util.math

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

fun BlockPos.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun BlockPos.toCenterVec(): Vec3 {
    return Vec3(this.x + 0.5, this.y + 0.5, this.z + 0.5)
}

fun BlockPos.toBottomCenterVec(): Vec3 {
    return Vec3(this.x + 0.5, this.y.toDouble(), this.z + 0.5)
}

fun BlockPos.horizontalDistanceTo(other: BlockPos): Double {
    val dx = (this.x - other.x).toDouble()
    val dz = (this.z - other.z).toDouble()
    return sqrt(dx * dx + dz * dz)
}

fun BlockPos.horizontalDistanceSqr(other: BlockPos): Double {
    val dx = (this.x - other.x).toDouble()
    val dz = (this.z - other.z).toDouble()
    return dx * dx + dz * dz
}