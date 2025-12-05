package org.kvxd.kiwi.pathing.execute.types

import org.kvxd.kiwi.control.InputHelper
import org.kvxd.kiwi.control.movement.ActionResult
import org.kvxd.kiwi.control.movement.actionResult
import org.kvxd.kiwi.control.movement.impl.Input
import org.kvxd.kiwi.control.movement.impl.Rotate
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object DropExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath): ActionResult {
        val result = actionResult()
        val targetPos = node.toVec()

        if (player.isOnGround || player.isTouchingWater) {
            return StandardExecutor.execute(node, path)
        }

        val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)
        if (distSq < 0.0025) {
            return result
        }

        val targetYaw = RotationUtils.getLookYaw(player.entityPos, targetPos)

        result += Rotate(yaw = targetYaw)
        result += Input(
            forward = true,
            sprint = false
        )

        result += InputHelper.airStrafe(targetPos, targetYaw)

        return result
    }
}