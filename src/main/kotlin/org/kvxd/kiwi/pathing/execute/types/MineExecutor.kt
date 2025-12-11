package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.level
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.math.RotationUtils
import org.kvxd.kiwi.util.math.toCenterVec

object MineExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean {
        val required = getRequiredBlocks(node)
        val blocksBroken = required.all { CollisionCache.isPassable(it) }

        if (blocksBroken) {
            return player.blockPosition() == node.pos
        }

        return false
    }

    override fun execute(node: Node, path: NodePath) {
        val required = getRequiredBlocks(node)

        val targetBlock = required.firstOrNull { CollisionCache.isSolid(it) }

        if (targetBlock == null) {
            StandardExecutor.execute(node, path)
            return
        }

        MovementController.stop()

        val state = level.getBlockState(targetBlock)
        if (!state.isAir) {
            MiningUtil.selectBestTool(state)
        }

        val center = targetBlock.toCenterVec()
        val rots = RotationUtils.getLookRotations(center)
        RotationManager.setTarget(rots.x, rots.y)

        InputOverride.state.attack = RotationUtils.isLookingAt(center, 0.6)
    }

    private fun getRequiredBlocks(node: Node): List<BlockPos> {
        val list = ArrayList<BlockPos>()
        list.add(node.pos)
        list.add(node.pos.above())
        return list
    }
}