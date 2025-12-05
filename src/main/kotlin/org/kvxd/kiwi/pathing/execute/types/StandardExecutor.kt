package org.kvxd.kiwi.pathing.execute.types

import org.kvxd.kiwi.control.InputHelper
import org.kvxd.kiwi.control.movement.ActionResult
import org.kvxd.kiwi.control.movement.actionResult
import org.kvxd.kiwi.control.movement.impl.Input
import org.kvxd.kiwi.control.movement.impl.Rotate
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object StandardExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath): ActionResult {
        val targetPos = node.toVec()
        val targetYaw = RotationUtils.getLookYaw(player.entityPos, targetPos)

        val result = actionResult()

        result += Rotate(yaw = targetYaw)
        result += Input(
            forward = true,
            sprint = InputHelper.shouldSprint(player, path)
        )

        val deltaY = targetPos.y - player.y
        if (node.type == MovementType.JUMP || (player.isTouchingWater && deltaY > 0)) {
            result += Input(
                jump = true
            )
        }

        return result
    }
}