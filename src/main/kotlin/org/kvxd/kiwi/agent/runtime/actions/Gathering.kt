package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import org.kvxd.kiwi.agent.ScanUtil
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.NavigationResult
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalNear
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.coroutine.waitClient
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private const val PICKUP_RADIUS_SQ = 2.0
private const val PICKUP_NAV_RADIUS = 1.35

suspend fun AgentRuntime.collectItems(itemId: String, amount: Int) {
    collectItems(setOf(itemId), amount)
}

suspend fun AgentRuntime.collectItems(itemIds: Set<String>, amount: Int) {
    phase = AgentPhase.COLLECTING
    InputOverride.capture()

    val acceptedIds = itemIds.filterTo(linkedSetOf()) { it.isNotBlank() }
    val label = acceptedIds.joinToString("|")
    var remaining = amount - inventoryCount(acceptedIds)
    var attempts = 0
    val skippedItems = mutableSetOf<Int>()

    while (remaining > 0 && attempts < ConfigData.collectMaxAttempts) {
        attempts++

        val targetItem = findSuitableItem(acceptedIds, skippedItems) ?: break

        val itemBlockPos = targetItem.blockPosition()
        val pickupStand = findPickupStand(targetItem)
        val goal = if (pickupStand != null) {
            GoalNear(pickupStand, 0.65)
        } else {
            GoalNear(itemBlockPos, PICKUP_NAV_RADIUS)
        }

        when (PathNavigator.navigateToGoal(goal)) {
            NavigationResult.Reached -> Unit
            is NavigationResult.Failed -> {
                skippedItems.add(targetItem.id)
                continue
            }
        }

        val preCount = inventoryCount(acceptedIds)
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
            waitClient(50.milliseconds)
            fineTicks++
        }
        MovementController.stop()

        if (fineTicks >= ConfigData.collectFineTuneTicks) {
            skippedItems.add(targetItem.id)
            continue
        }

        var waitTicks = 0
        while (inventoryCount(acceptedIds) <= preCount && waitTicks < ConfigData.collectTimeoutTicks) {
            waitClient(50.milliseconds)
            waitTicks++
        }

        if (inventoryCount(acceptedIds) > preCount) {
            remaining = amount - inventoryCount(acceptedIds)
        } else {
            skippedItems.add(targetItem.id)
        }
    }

    if (inventoryCount(acceptedIds) < amount) {
        throw AgentFailure("Failed to collect enough $label after ${ConfigData.collectMaxAttempts} attempts")
    }
    MovementController.stop()
}

private fun AgentRuntime.findSuitableItem(
    itemIds: Set<String>,
    skippedItems: Set<Int>
): ItemEntity? {
    val items = ScanUtil.scanNearbyDroppedItems(radius = ConfigData.dropScanRadius, itemIds = itemIds)
    return items
        .asSequence()
        .filter { item ->
            item.id !in skippedItems &&
                    item.onGround() &&
                    item.deltaMovement.lengthSqr() < 0.01 &&
                    abs(item.y - player.y) < 2.5
        }
        .sortedBy { player.position().distanceToSqr(it.position()) }
        .firstOrNull()
}

private fun findPickupStand(item: ItemEntity): BlockPos? {
    val itemPos = item.blockPosition()
    val playerPos = player.blockPosition()
    val candidates = buildList {
        add(itemPos)
        for (dir in Direction.Plane.HORIZONTAL) {
            add(itemPos.relative(dir))
        }
    }

    return candidates
        .distinct()
        .filter { CollisionCache.isWalkable(it) }
        .minByOrNull { playerPos.distSqr(it) }
}

private fun ScanUtil.scanNearbyDroppedItems(radius: Int, itemIds: Set<String>): List<ItemEntity> {
    val aabb = net.minecraft.world.phys.AABB.ofSize(
        net.minecraft.world.phys.Vec3.atCenterOf(player.blockPosition()),
        radius * 2.0, radius * 2.0, radius * 2.0
    )
    return level.getEntities(null, aabb) { it is ItemEntity }
        .map { it as ItemEntity }
        .filter {
            val stack = it.item
            !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path in itemIds
        }
}
