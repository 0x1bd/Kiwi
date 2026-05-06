package org.kvxd.kiwi.agent.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.PathResult
import org.kvxd.kiwi.agent.pathing.calc.PathSearchDiagnostics
import org.kvxd.kiwi.agent.pathing.calc.ThetaStar
import org.kvxd.kiwi.agent.pathing.goal.Goal

object PathPlanner {

    suspend fun calculate(start: BlockPos, goal: Goal): PathResult {
        return withContext(Dispatchers.Default) {
            CollisionCache.clearCache()
            PathSearchDiagnostics.reset()
            val result = ThetaStar().calculate(start, goal)
            CollisionCache.clearCache()
            result
        }
    }
}
