package org.kvxd.kiwi.agent.pathing.execute.types

import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.agent.pathing.execute.ExecutionMiningUtil
import org.kvxd.kiwi.agent.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils

object StandardExecutor : MovementExecutor {

    override val deviationThreshold: Double
        get() = 0.8

    override fun isFinished(node: Node): Boolean {
        return when (node.type) {
            MovementType.JUMP -> {
                val onTargetLevel = player.position().y >= node.pos.y - 0.5
                val grounded = player.onGround() || player.isInWater
                onTargetLevel && grounded
            }
            else -> true
        }
    }

    override fun execute(node: Node, path: NodePath) {
        val requiredBlocks = mutableListOf(node.pos, node.pos.above())
        if (node.type == MovementType.JUMP) {
            val startPos = path.previous()?.pos ?: player.blockPosition()
            requiredBlocks.add(startPos.above(2))
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

        InputOverride.update {
            attack = false
            jump = false
            sprint = false
        }

        val currentTarget = node.toVec()

        val rotations = RotationUtils.getLookRotations(currentTarget)
        RotationManager.setTarget(yaw = rotations.x, pitch = 0f)

        MovementController.moveToward(currentTarget)

        InputOverride.update {
            sprint = MovementController.shouldSprint(path)
        }

        if (node.type == MovementType.JUMP) {
            val belowTarget = player.position().y < node.pos.y - 0.2
            val falling = player.deltaMovement.y < -0.1
            if (belowTarget && !falling) {
                InputOverride.update { jump = true }
            }
        } else if (player.isInWater) {
            if (currentTarget.y > player.y || player.isUnderWater) {
                InputOverride.update { jump = true }
            }
        }
    }
}