package org.kvxd.kiwi.agent.pathing.execute.types

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.agent.pathing.execute.ExecutionMiningUtil
import org.kvxd.kiwi.agent.pathing.execute.MovementExecutor
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
        val requiredBlocks = listOf(node.pos, node.pos.above())
        val miningActive = if (node.miningBlocks.isNotEmpty()) {
            ExecutionMiningUtil.minePlannedBlocks(node.miningBlocks)
        } else {
            ExecutionMiningUtil.mineObstructions(requiredBlocks)
        }

        if (miningActive) {
            InputOverride.update {
                jump = false
                sprint = false
                use = false
            }
            return
        }
        InputOverride.update { attack = false }

        if (!InventoryUtil.ensureInHotbar({ stack ->
                val blockItem = stack.item as? BlockItem
                if (blockItem == null) false
                else {
                    val blockId = BuiltInRegistries.BLOCK.getKey(blockItem.block).path
                    blockId in ConfigData.allowedBuildBlockIds
                }
            }, { stack ->
                val blockItem = stack.item as? BlockItem ?: return@ensureInHotbar false
                val blockId = BuiltInRegistries.BLOCK.getKey(blockItem.block).path
                blockId in Agent.context.minedItemIds
            })) {
            ClientMessenger.error("No allowed blocks in inventory for pillaring")
            PathNavigator.stop()
            return
        }

        RotationManager.setTarget(pitch = 90f)

        if (!MovementController.alignToBlockCenter(node.pos)) {
            InputOverride.update {
                jump = false
                use = false
            }
            return
        }

        val placeTarget = node.pos.below()
        val currentY = player.position().y

        CollisionCache.invalidate(placeTarget)

        if (player.onGround() || player.deltaMovement.y > 0) {
            InputOverride.update { jump = true }
        }

        if (!CollisionCache.isSolid(placeTarget)) {
            if (currentY > placeTarget.y + 0.4) {
                InputOverride.update { use = true }
                Agent.context.placedPositions.add(placeTarget)
                CollisionCache.invalidate(placeTarget)
            } else {
                InputOverride.update { use = false }
            }
        } else {
            Agent.context.placedPositions.add(placeTarget)
            InputOverride.update { use = false }
        }
    }
}