package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

data class Node(
    val pos: BlockPos,
    var parent: Node?,
    var costG: Double,
    val costH: Double,
    val type: MovementType,
    var heapIndex: Int = -1
) : Comparable<Node> {

    val costF: Double get() = costG + costH

    val posLong: Long = pos.asLong()

    override fun compareTo(other: Node): Int {
        return costF.compareTo(other.costF)
    }

    fun toVec(): Vec3d = Vec3d.ofBottomCenter(pos)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Node
        return posLong == other.posLong
    }

    override fun hashCode(): Int {
        return posLong.hashCode()
    }
}