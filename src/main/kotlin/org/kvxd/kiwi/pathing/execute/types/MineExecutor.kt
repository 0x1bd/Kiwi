package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import org.kvxd.kiwi.client
import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.math.RotationUtils
import org.kvxd.kiwi.level

object MineExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean {
        val required = getRequiredBlocks(node)
        val blocksBroken = required.all { CollisionCache.isPassable(it) }

        return blocksBroken
    }

    override fun execute(node: Node, path: NodePath) {
        MovementController.stop()

        val required = getRequiredBlocks(node)
        val targetBlock = required.firstOrNull { CollisionCache.isSolid(it) }

        if (targetBlock == null) {
            if (node.pos.y > player.blockPosition().y) {
                InputOverride.state.jump = true
            }
            StandardExecutor.execute(node, path)
            return
        }

        val state = level.getBlockState(targetBlock)
        if (!state.isAir) {
            MiningUtil.selectBestTool(state)
        }

        val center = targetBlock.center
        val rots = RotationUtils.getLookRotations(center)
        RotationManager.setTarget(rots.x, rots.y)

        if (RotationUtils.isLookingAt(center, 0.6)) {
            client.gameMode?.continueDestroyBlock(
                targetBlock,
                RotationUtils.getDirection(targetBlock)
            )
            client.player?.swing(InteractionHand.MAIN_HAND)
        } else {
            client.gameMode?.stopDestroyBlock()
        }
    }

    private fun getRequiredBlocks(node: Node): List<BlockPos> {
        val parent = node.parent ?: return emptyList()
        val delta = node.pos.subtract(parent.pos)

        val list = ArrayList<BlockPos>()

        if (delta.y > 0) {
            list.add(node.pos)
            list.add(node.pos.above())
        } else if (delta.y < 0) {
            list.add(node.pos)
            list.add(node.pos.above())
        } else {
            list.add(node.pos)
            list.add(node.pos.above())
        }
        return list
    }
}