package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.client
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.goal.Goal

class RepathThread(
    private val start: BlockPos,
    private val goal: Goal,
    private val onComplete: (PathResult) -> Unit
) : Thread() {

    override fun run() {
        CollisionCache.clearCache()

        val result = AStar().calculate(start, goal)

        CollisionCache.clearCache()

        client.execute {
            onComplete(result)
        }
    }
}