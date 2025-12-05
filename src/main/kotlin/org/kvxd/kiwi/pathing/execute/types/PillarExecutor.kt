package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.item.BlockItem
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.control.MovementController
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.RotationUtils
import org.kvxd.kiwi.util.WorldUtils
import kotlin.math.abs

object PillarExecutor : MovementExecutor {

    override val deviationThreshold: Double
        get() = 0.65

    override fun isFinished(node: Node): Boolean {
        return CollisionCache.isSolid(node.pos.down())
    }

    override fun execute(node: Node, path: NodePath) {
        if (!InventoryUtil.selectSlot { it.item is BlockItem }) {
            ClientMessenger.error("No blocks")
            PathExecutor.stop()
            return
        }

        RotationManager.setTarget(pitch = 90f)

        if (player.isOnGround)
            handleGroundAlign()

        WorldUtils.placeBlockBelow(node.pos)
    }

    private fun handleGroundAlign() {
        val center = Vec3d.ofBottomCenter(player.blockPos)
        val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, center)

        if (distSq > 0.05) {
            MovementController.moveToward(player, center, 0.05)
        } else {
            val safeVelocity = 0.1
            if (abs(player.velocity.x) < safeVelocity && abs(player.velocity.z) < safeVelocity) {
                InputOverride.state.jump = true
            }
        }
    }
}