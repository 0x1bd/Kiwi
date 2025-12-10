package org.kvxd.kiwi.pathing.goal.goals

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.goal.Goal
import kotlin.math.sqrt

class GoalXYZ(private val target: BlockPos) : Goal {

    override fun hasReached(pos: BlockPos): Boolean {
        if (pos == target) return true

        if (!ConfigData.strictPosition) {
            if (pos == target.above()) {
                return CollisionCache.isSolid(target)
            }
        }

        return false
    }

    override fun getHeuristic(pos: BlockPos): Double {
        return sqrt(pos.distSqr(target))
    }

    override fun getApproximateTarget(): BlockPos = target
}