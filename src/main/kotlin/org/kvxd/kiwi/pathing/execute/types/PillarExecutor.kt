package org.kvxd.kiwi.pathing.execute.types

import net.minecraft.world.item.BlockItem
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

object PillarExecutor : MovementExecutor {

    override val deviationThreshold: Double
        get() = 0.65

    override fun isFinished(node: Node): Boolean {
        return CollisionCache.isSolid(node.pos.below()) && player.position().y >= node.pos.y
    }

    override fun execute(node: Node, path: NodePath) {
        if (!InventoryUtil.selectSlot { it.item is BlockItem }) {
            ClientMessenger.error("No blocks")
            PathExecutor.stop()
            return
        }

        RotationManager.setTarget(pitch = 90f)

        if (!MovementController.alignToBlockCenter(node.pos)) {
            InputOverride.state.jump = false
            InputOverride.state.use = false
            return
        }

        val placeTarget = node.pos.below()
        val currentY = player.position().y

        if (player.onGround() || player.deltaMovement.y > 0) {
            InputOverride.state.jump = true
        }

        if (!CollisionCache.isSolid(placeTarget)) {
            val dist = currentY - (placeTarget.y + 1.0)

            if (currentY > placeTarget.y + 0.4) {
                InputOverride.state.use = true
            } else {
                InputOverride.state.use = false
            }
        } else {
            InputOverride.state.use = false
        }
    }
}