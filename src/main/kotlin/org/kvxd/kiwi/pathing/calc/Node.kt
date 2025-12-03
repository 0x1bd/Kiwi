package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

data class Node(
    val pos: BlockPos,
    val parent: Node?,
    val costG: Double,
    val costH: Double
) : Comparable<Node> {

    val costF: Double get() = costG + costH

    override fun compareTo(other: Node): Int {
        return costF.compareTo(other.costF)
    }

    fun toVec(): Vec3d = Vec3d.ofBottomCenter(pos)
}