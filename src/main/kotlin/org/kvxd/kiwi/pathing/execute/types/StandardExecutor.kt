package org.kvxd.kiwi.pathing.execute.types

import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils

object StandardExecutor : MovementExecutor {

    override val deviationThreshold: Double
        get() = 0.8

    override fun isFinished(node: Node): Boolean = true

    override fun execute(node: Node, path: NodePath) {
        val currentTarget = node.toVec()

        val rotations = RotationUtils.getLookRotations(currentTarget)
        RotationManager.setTarget(yaw = rotations.x, pitch = 0f)

        MovementController.moveToward(currentTarget)

        InputOverride.state.sprint = MovementController.shouldSprint(path)

        if (node.type == MovementType.JUMP) {
            InputOverride.state.jump = true
        } else if (player.isInWater) {
            if (currentTarget.y > player.y || player.isUnderWater) {
                InputOverride.state.jump = true
            }
        }
    }
}