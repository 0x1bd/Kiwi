package org.kvxd.kiwi.agent.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.level
import org.kvxd.kiwi.util.MiningUtil

object MovementObstructionUtil {

    private const val TIME_PENALTY = 3.0
    private const val BASE_MINING_COST = 6.0

    data class MiningPlan(
        val blocks: List<BlockPos>,
        val cost: Double
    )

    fun calculateMiningCost(blocks: List<BlockPos>): Double? {
        return planMining(blocks)?.cost
    }

    fun planMining(blocks: List<BlockPos>): MiningPlan? {
        var totalTime = 0.0
        val miningBlocks = mutableListOf<BlockPos>()

        for (pos in blocks) {
            if (CollisionCache.isSolid(pos)) {
                if (!ConfigData.allowBreak) return null

                if (!canMineForNavigation(pos)) return null

                if (!CollisionCache.isSafeToMine(pos)) return null

                val time = MiningUtil.getBreakTime(pos)
                if (time.isInfinite()) return null

                totalTime += time
                miningBlocks.add(pos)
            } else if (CollisionCache.isDangerous(pos)) {
                return null
            }
        }

        if (miningBlocks.isEmpty()) return MiningPlan(emptyList(), 0.0)

        return MiningPlan(
            blocks = miningBlocks.distinct(),
            cost = totalTime * TIME_PENALTY + BASE_MINING_COST
        )
    }

    private fun canMineForNavigation(pos: BlockPos): Boolean {
        if (pos in Agent.context.placedPositions) return false

        val block = level.getBlockState(pos).block
        return block in ConfigData.safeToMineBlockTypes || CollisionCache.isLeaf(pos)
    }
}