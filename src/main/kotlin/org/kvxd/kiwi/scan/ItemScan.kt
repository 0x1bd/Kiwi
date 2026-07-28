package org.kvxd.kiwi.scan

import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player

object ItemScan {

    fun nearby(itemIds: IntArray, radius: Double): List<ItemEntity> {
        if (itemIds.isEmpty()) return emptyList()
        val box = AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2)
        return level.getEntities(player, box) { entity ->
            entity is ItemEntity && matches(entity, itemIds)
        }.mapNotNull { it as? ItemEntity }
    }

    fun nearest(itemIds: IntArray, radius: Double): ItemEntity? =
        nearby(itemIds, radius).minByOrNull { it.distanceToSqr(player) }

    private fun matches(entity: ItemEntity, itemIds: IntArray): Boolean {
        val stack = entity.item
        if (stack.isEmpty) return false
        val id = Ids.itemOf(stack)
        for (candidate in itemIds) if (candidate == id) return true
        return false
    }
}
