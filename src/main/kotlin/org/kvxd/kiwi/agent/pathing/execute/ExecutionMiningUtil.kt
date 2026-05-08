package org.kvxd.kiwi.agent.pathing.execute

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.registryPath
import org.kvxd.kiwi.util.math.RotationUtils

object ExecutionMiningUtil {

    fun mineObstructions(requiredBlocks: List<BlockPos>): Boolean {
        requiredBlocks.forEach { CollisionCache.invalidate(it) }

        val targetBlock = requiredBlocks.firstOrNull { canMineExecutionObstruction(it) } ?: run {
            clearMiningDebug()
            return false
        }

        return mineTarget(targetBlock)
    }

    fun minePlannedBlocks(plannedBlocks: List<BlockPos>): Boolean {
        if (plannedBlocks.isEmpty()) {
            clearMiningDebug()
            return false
        }
        plannedBlocks.forEach { CollisionCache.invalidate(it) }
        val targetBlock = plannedBlocks.firstOrNull { canMineExecutionObstruction(it) } ?: run {
            clearMiningDebug()
            return false
        }
        return mineTarget(targetBlock)
    }

    private fun canMineExecutionObstruction(pos: BlockPos): Boolean {
        return pos !in Agent.context.placedPositions && CollisionCache.isSolid(pos)
    }

    private fun mineTarget(targetBlock: BlockPos): Boolean {
        MovementController.stop()

        val state = level.getBlockState(targetBlock)
        DebugState.agentMineTarget = targetBlock
        DebugState.agentMineBlockId = state.block.registryPath

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

    private fun clearMiningDebug() {
        DebugState.agentMineTarget = null
        DebugState.agentMineBlockId = ""
    }
}