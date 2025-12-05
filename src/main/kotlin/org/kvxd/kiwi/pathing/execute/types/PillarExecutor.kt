package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.item.BlockItem
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.control.InputHelper
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.control.movement.ActionResult
import org.kvxd.kiwi.control.movement.actionResult
import org.kvxd.kiwi.control.movement.impl.Input
import org.kvxd.kiwi.control.movement.impl.Interact
import org.kvxd.kiwi.control.movement.impl.Interaction
import org.kvxd.kiwi.control.movement.impl.Rotate
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.RotationUtils
import kotlin.math.abs

object PillarExecutor : MovementExecutor {

    override val deviationThreshold: Double
        get() = 0.65

    override fun isFinished(node: Node): Boolean {
        return CollisionCache.isSolid(node.pos.down())
    }

    override fun execute(node: Node, path: NodePath): ActionResult {
        val result = actionResult()

        if (!InventoryUtil.selectSlot { it.item is BlockItem }) {
            ClientMessenger.error("No blocks")
            PathExecutor.stop()
            return result
        }

        result += Rotate(pitch = 90f)

        if (player.isOnGround) {
            result += handleGroundAlign()
        }

        result += Interact(
            action = Interaction.RIGHT_CLICK,
            pos = node.pos.down(),
            face = Direction.UP
        )

        return result
    }

    private fun handleGroundAlign(): ActionResult {
        val result = actionResult()
        val center = Vec3d.ofBottomCenter(player.blockPos)
        val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, center)

        if (distSq > 0.05) {
            result += InputHelper.moveTowardCenter(center)
            return result
        }

        val safeVelocity = 0.1
        if (abs(player.velocity.x) < safeVelocity &&
            abs(player.velocity.z) < safeVelocity) {

            result += Input(jump = true)
        }

        return result
    }
}