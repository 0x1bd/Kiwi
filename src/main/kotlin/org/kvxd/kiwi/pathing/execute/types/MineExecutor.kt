package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
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
import org.kvxd.kiwi.util.RotationUtils
import org.kvxd.kiwi.world

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
            if (node.pos.y > player.blockPos.y) {
                InputOverride.state.jump = true
            }
            StandardExecutor.execute(node, path)
            return
        }

        val state = world.getBlockState(targetBlock)
        if (state != null && !state.isAir) {
            MiningUtil.selectBestTool(state)
        }

        val center = targetBlock.toCenterPos()
        val rots = RotationUtils.getLookRotations(center)
        RotationManager.setTarget(rots.x, rots.y)

        if (RotationUtils.isLookingAt(center, 0.6)) {
            client.interactionManager?.updateBlockBreakingProgress(
                targetBlock,
                RotationUtils.getDirection(targetBlock)
            )
            client.player?.swingHand(Hand.MAIN_HAND)
        } else {
            client.interactionManager?.cancelBlockBreaking()
        }
    }

    private fun getRequiredBlocks(node: Node): List<BlockPos> {
        val parent = node.parent ?: return emptyList()
        val delta = node.pos.subtract(parent.pos)

        val list = ArrayList<BlockPos>()

        if (delta.y > 0) {
            list.add(node.pos)
            list.add(node.pos.up())
        } else if (delta.y < 0) {
            list.add(node.pos)
            list.add(node.pos.up())
        } else {
            list.add(node.pos)
            list.add(node.pos.up())
        }
        return list
    }
}