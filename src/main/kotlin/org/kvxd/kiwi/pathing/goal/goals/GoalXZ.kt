package org.kvxd.kiwi.pathing.goal.goals

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.pathing.goal.Goal
import kotlin.math.sqrt

class GoalXZ(private val x: Int, private val z: Int) : Goal {

    override fun getHeuristic(pos: BlockPos): Double {
        val dx = (pos.x - x).toDouble()
        val dz = (pos.z - z).toDouble()

        return sqrt(dx * dx + dz * dz)
    }

    override fun hasReached(pos: BlockPos): Boolean = pos.x == x && pos.z == z

    override fun getApproximateTarget(): BlockPos = BlockPos(x, 0, z)
}