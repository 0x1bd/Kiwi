package org.kvxd.kiwi.pathing.goal.goals

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.goal.Goal
import kotlin.math.sqrt

class GoalNear(private val target: BlockPos, private val range: Double) : Goal {

    private val rangeSq = range * range

    override fun getHeuristic(pos: BlockPos): Double {
        val dist = sqrt(pos.getSquaredDistance(target))
        return (dist - range).coerceAtLeast(0.0)
    }

    override fun hasReached(pos: BlockPos): Boolean {
        return pos.getSquaredDistance(target) <= rangeSq
    }

    override fun getApproximateTarget(): BlockPos = target
}