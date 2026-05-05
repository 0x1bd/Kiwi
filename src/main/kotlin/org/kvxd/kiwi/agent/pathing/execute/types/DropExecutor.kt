package org.kvxd.kiwi.agent.pathing.execute.types

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.agent.pathing.execute.ExecutionMiningUtil
import org.kvxd.kiwi.agent.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils

object DropExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean {
        return player.position().y <= node.pos.y + 1.0 && (player.onGround() || player.isInWater)
    }

    override fun execute(node: Node, path: NodePath) {
        val prevNode = path.previous()

        val requiredBlocks = mutableListOf(node.pos, node.pos.above())
        if (prevNode != null) {
            val minY = minOf(prevNode.pos.y, node.pos.y)
            val maxY = maxOf(prevNode.pos.y, node.pos.y)
            for (y in minY..maxY) {
                requiredBlocks.add(net.minecraft.core.BlockPos(node.pos.x, y, node.pos.z))
                requiredBlocks.add(net.minecraft.core.BlockPos(node.pos.x, y + 1, node.pos.z))
            }
        }

        val miningActive = if (node.miningBlocks.isNotEmpty()) {
            ExecutionMiningUtil.minePlannedBlocks(node.miningBlocks)
        } else {
            ExecutionMiningUtil.mineObstructions(requiredBlocks)
        }

        if (miningActive) {
            InputOverride.update {
                jump = false
                sprint = false
            }
            return
        }

        InputOverride.update { attack = false }
        val targetPos = node.toVec()

        val targetYaw = if (prevNode != null) {
            RotationUtils.getLookYaw(prevNode.toVec(), targetPos)
        } else {
            RotationUtils.getLookYaw(player.position(), targetPos)
        }

        RotationManager.setTarget(yaw = targetYaw)

        val horizontalTarget = Vec3(targetPos.x, player.position().y, targetPos.z)
        MovementController.moveToward(horizontalTarget)

        InputOverride.update { sprint = false }
    }
}