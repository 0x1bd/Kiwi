package org.kvxd.kiwi.pathing.execute.types

import org.kvxd.kiwi.client
import org.kvxd.kiwi.control.InputController
import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.RotationUtils

object DropExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath) {
        val targetPos = node.toVec()

        if (player.isOnGround || player.isTouchingWater) {
            StandardExecutor.execute(node, path)
            return
        }

        val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)
        if (distSq < 0.0025) return

        val targetYaw = RotationUtils.getLookYaw(player.entityPos, targetPos)
        MovementController.applyAirStrafe(player, targetPos, targetYaw)

        RotationManager.setTarget(targetYaw, 0f)
        InputController.sprint = false
    }
}