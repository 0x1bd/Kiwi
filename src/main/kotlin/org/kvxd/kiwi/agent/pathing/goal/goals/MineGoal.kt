package org.kvxd.kiwi.agent.pathing.goal.goals

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.goal.Goal
import kotlin.math.sqrt

class MineGoal(private val target: BlockPos) : Goal {

    override fun hasReached(pos: BlockPos): Boolean {
        return !CollisionCache.isSolid(target) && !CollisionCache.isSolid(target.above())
    }

    override fun getHeuristic(pos: BlockPos): Double {
        return sqrt(pos.distSqr(target))
    }

    override fun getApproximateTarget(): BlockPos {
        for (dir in Direction.Plane.HORIZONTAL) {
            val adj = target.relative(dir)
            if (CollisionCache.isWalkable(adj) || CollisionCache.isPassable(adj) || CollisionCache.isLeaf(adj)) {
                return adj
            }
        }
        val above = target.above()
        if (CollisionCache.isPassable(above) || CollisionCache.isLeaf(above)) return above
        val below = target.below()
        if (CollisionCache.isWalkable(below) || CollisionCache.isPassable(below) || CollisionCache.isLeaf(below)) return below
        return target
    }
}