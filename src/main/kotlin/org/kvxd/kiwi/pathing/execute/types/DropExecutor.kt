package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RotationUtils

object DropExecutor : MovementExecutor {

    override fun isFinished(node: Node): Boolean {
        return player.position().y <= node.pos.y + 1.0 && (player.onGround() || player.isInWater)
    }

    override fun execute(node: Node, path: NodePath) {
        val targetPos = node.toVec()
        val prevNode = path.previous()

        val targetYaw = if (prevNode != null) {
            RotationUtils.getLookYaw(prevNode.toVec(), targetPos)
        } else {
            RotationUtils.getLookYaw(player.position(), targetPos)
        }

        RotationManager.setTarget(yaw = targetYaw)

        val horizontalTarget = Vec3(targetPos.x, player.position().y, targetPos.z)
        MovementController.moveToward(horizontalTarget)

        InputOverride.state.sprint = false
    }
}