package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.agent.ScanUtil
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalXYZ
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import kotlinx.coroutines.delay
import kotlin.math.abs

// Minecraft's item pickup radius is 1.5 blocks (squared = 2.25).
// We use a slightly tighter threshold so we don't declare success before the server confirms pickup.
private const val PICKUP_RADIUS_SQ = 2.0

suspend fun AgentRuntime.collectItems(itemId: String, amount: Int) {
    phase = AgentPhase.COLLECTING
    InputOverride.capture()

    var remaining = amount - inventoryCount(itemId)
    var attempts = 0
    // Track items that we have already failed to reach so we don't loop on them.
    val skippedItems = mutableSetOf<Int>() // entity IDs

    while (remaining > 0 && attempts < ConfigData.collectMaxAttempts) {
        attempts++

        val targetItem = findSuitableItem(itemId, skippedItems) ?: break

        val itemBlockPos = targetItem.blockPosition()
        val goal = GoalXYZ(itemBlockPos)
        PathNavigator.navigateToGoal(goal)

        val preCount = inventoryCount(itemId)
        var fineTicks = 0

        while (fineTicks < ConfigData.collectFineTuneTicks) {
            if (targetItem.isRemoved) {
                // Item was picked up by someone else or despawned — stop fine-tuning
                break
            }

            // If the item moved significantly (e.g. pushed by physics) abort and retry
            if (targetItem.deltaMovement.lengthSqr() > 0.01) {
                break
            }

            val currentItemPos = targetItem.position()

            // Check horizontal proximity: the bot only needs to stand next to the block the
            // item is resting on. We deliberately do NOT constrain Y here because items
            // sitting on top of a block are at eye-level from below but are still picked up
            // when the player walks into the side of that block.
            val dx = player.position().x - currentItemPos.x
            val dz = player.position().z - currentItemPos.z
            val distSq = dx * dx + dz * dz

            if (distSq <= PICKUP_RADIUS_SQ) {
                break
            }

            MovementController.moveToward(currentItemPos, threshold = 0.05)
            delay(50)
            fineTicks++
        }

        if (fineTicks >= ConfigData.collectFineTuneTicks) {
            // We exhausted fine-tune ticks without reaching this item.
            // Mark it as skipped and try a different one instead of hard-failing.
            skippedItems.add(targetItem.id)
            continue
        }

        var waitTicks = 0
        while (inventoryCount(itemId) <= preCount && waitTicks < ConfigData.collectTimeoutTicks) {
            delay(50)
            waitTicks++
        }

        if (inventoryCount(itemId) > preCount) {
            remaining = amount - inventoryCount(itemId)
        } else {
            // Pickup didn't register — item may be unreachable. Skip it.
            skippedItems.add(targetItem.id)
        }
    }

    if (inventoryCount(itemId) < amount) {
        throw AgentFailure("Failed to collect enough $itemId after ${ConfigData.collectMaxAttempts} attempts")
    }
}

private suspend fun AgentRuntime.findSuitableItem(
    itemId: String,
    skippedItems: Set<Int>
): ItemEntity? {
    val items = ScanUtil.scanNearbyDroppedItems(radius = ConfigData.dropScanRadius, itemId = itemId)
    return items.firstOrNull { item ->
        item.id !in skippedItems &&
                item.onGround() &&
                item.deltaMovement.lengthSqr() < 0.01 &&
                // Items on top of blocks are at blockY + 0.25, which can be up to ~2 blocks
                // above the player's feet. Allow a generous vertical window so we don't
                // reject items that are actually reachable by walking up to the block.
                abs(item.y - player.y) < 2.5
    }
}

private fun ScanUtil.scanNearbyDroppedItems(radius: Int, itemId: String): List<ItemEntity> {
    val aabb = net.minecraft.world.phys.AABB.ofSize(
        net.minecraft.world.phys.Vec3.atCenterOf(player.blockPosition()),
        radius * 2.0, radius * 2.0, radius * 2.0
    )
    return level.getEntities(null, aabb) { it is ItemEntity }
        .map { it as ItemEntity }
        .filter {
            val stack = it.item
            !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path == itemId
        }
}
