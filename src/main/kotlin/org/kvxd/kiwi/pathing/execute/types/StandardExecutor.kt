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

    override val deviationThreshold: Double
        get() = 0.8

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath) {
        val targetPos = node.toVec()
        val targetYaw = RotationUtils.getLookYaw(player.position(), targetPos)

        RotationManager.setTarget(yaw = targetYaw)
        MovementController.forward()

        InputOverride.state.sprint = MovementController.shouldSprint(path)

        if (node.type == MovementType.JUMP) {
            InputOverride.state.jump = true
        } else if (player.isInWater && targetPos.y > player.y) {
            InputOverride.state.jump = true
        }
    }
}