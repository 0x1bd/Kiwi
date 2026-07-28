package org.kvxd.kiwi.control

import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import org.kvxd.kiwi.player

object ToolSelector {

    fun hotbar(): List<ItemStack> = (0..8).map { player.inventory.getItem(it) }

    fun inventory(): List<ItemStack> = (0..35).map { player.inventory.getItem(it) }

    fun equipBest(state: BlockState): Boolean {
        var bestSlot = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (slot in 0..35) {
            val score = BreakSpeed.toolScore(player.inventory.getItem(slot), state)
            if (score > bestScore) {
                bestScore = score
                bestSlot = slot
            }
        }
        if (bestSlot < 0) return false
        if (bestSlot == player.inventory.selectedSlot) return true
        return Containers.equipInventorySlot(bestSlot)
    }
}
