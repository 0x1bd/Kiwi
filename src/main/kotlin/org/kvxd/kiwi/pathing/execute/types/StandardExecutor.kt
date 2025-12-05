package org.kvxd.kiwi.pathing.execute.types

import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object StandardExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath) {
        val targetPos = node.toVec()
        val targetYaw = RotationUtils.getLookYaw(player.entityPos, targetPos)

        RotationManager.setTarget(targetYaw, player.pitch)

        MovementController.forward()

        InputOverride.state.sprint = MovementController.shouldSprint(player, path)

        val deltaY = targetPos.y - player.y
        if (node.type == MovementType.JUMP || (player.isTouchingWater && deltaY > 0)) {
            InputOverride.state.jump = true
        }
    }
}