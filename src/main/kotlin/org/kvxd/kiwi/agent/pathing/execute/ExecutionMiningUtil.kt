package org.kvxd.kiwi.agent.pathing.execute

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.math.RotationUtils

object ExecutionMiningUtil {

    /**
     * Checks if there are any solid blocks in the provided list.
     * If so, equips the right tool and attacks the first solid block.
     * Returns true if a block is currently being mined, false if the path is clear.
     */
    fun mineObstructions(requiredBlocks: List<BlockPos>): Boolean {
        // Invalidate cache for the required blocks to detect when they break
        requiredBlocks.forEach { CollisionCache.invalidate(it) }

        val targetBlock = requiredBlocks.firstOrNull { canMineExecutionObstruction(it) } ?: return false

        return mineTarget(targetBlock)
    }

    fun minePlannedBlocks(plannedBlocks: List<BlockPos>): Boolean {
        if (plannedBlocks.isEmpty()) return false
        plannedBlocks.forEach { CollisionCache.invalidate(it) }
        val targetBlock = plannedBlocks.firstOrNull { canMineExecutionObstruction(it) } ?: return false
        return mineTarget(targetBlock)
    }

    private fun canMineExecutionObstruction(pos: BlockPos): Boolean {
        return pos !in Agent.context.placedPositions && CollisionCache.isSolid(pos)
    }

    private fun mineTarget(targetBlock: BlockPos): Boolean {
        MovementController.stop()

        val state = level.getBlockState(targetBlock)
        if (!state.isAir) {
            MiningUtil.selectBestTool(state)
        }

        val targetPos = RotationUtils.getClosestPointOnBlock(targetBlock, player.eyePosition)
        val rots = RotationUtils.getLookRotations(targetPos)
        RotationManager.setTarget(rots.x, rots.y)

        InputOverride.update {
            attack = RotationUtils.isLookingAt(targetPos, 0.6)
        }

        return true
    }
}
