package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
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
import kotlin.time.Duration.Companion.milliseconds

private const val PICKUP_RADIUS_SQ = 2.0

suspend fun AgentRuntime.collectItems(itemId: String, amount: Int) {
    phase = AgentPhase.COLLECTING
    InputOverride.capture()

    var remaining = amount - inventoryCount(itemId)
    var attempts = 0
    val skippedItems = mutableSetOf<Int>()

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
                break
            }

            if (targetItem.deltaMovement.lengthSqr() > 0.01) {
                break
            }

            val currentItemPos = targetItem.position()

            val dx = player.position().x - currentItemPos.x
            val dz = player.position().z - currentItemPos.z
            val distSq = dx * dx + dz * dz

            if (distSq <= PICKUP_RADIUS_SQ) {
                break
            }

            MovementController.moveToward(currentItemPos, threshold = 0.05)
            delay(50.milliseconds)
            fineTicks++
        }

        if (fineTicks >= ConfigData.collectFineTuneTicks) {
            skippedItems.add(targetItem.id)
            continue
        }

        var waitTicks = 0
        while (inventoryCount(itemId) <= preCount && waitTicks < ConfigData.collectTimeoutTicks) {
            delay(50.milliseconds)
            waitTicks++
        }

        if (inventoryCount(itemId) > preCount) {
            remaining = amount - inventoryCount(itemId)
        } else {
            skippedItems.add(targetItem.id)
        }
    }

    if (inventoryCount(itemId) < amount) {
        throw AgentFailure("Failed to collect enough $itemId after ${ConfigData.collectMaxAttempts} attempts")
    }
}

private fun AgentRuntime.findSuitableItem(
    itemId: String,
    skippedItems: Set<Int>
): ItemEntity? {
    val items = ScanUtil.scanNearbyDroppedItems(radius = ConfigData.dropScanRadius, itemId = itemId)
    return items.firstOrNull { item ->
        item.id !in skippedItems &&
                item.onGround() &&
                item.deltaMovement.lengthSqr() < 0.01 &&
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
