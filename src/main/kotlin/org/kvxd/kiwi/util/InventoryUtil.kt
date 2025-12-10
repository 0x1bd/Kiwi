package org.kvxd.kiwi.util

import net.minecraft.world.item.ItemStack
import org.kvxd.kiwi.player

object InventoryUtil {

    fun findBestSlot(score: (ItemStack) -> Float): Int {
        val inv = player.inventory

        var bestSlot = -1
        var bestScore = Float.NEGATIVE_INFINITY

        for (i in 0 until 9) {
            val stack = inv.getItem(i)
            val s = score(stack)

            if (s > bestScore) {
                bestScore = s
                bestSlot = i
            }
        }

        return bestSlot
    }

    fun selectSlot(block: (ItemStack) -> Boolean): Boolean {
        val inv = player.inventory

        for (i in 0 until 9) {
            val stack = inv.getItem(i)
            if (block(stack)) {
                inv.selectedSlot = i
                return true
            }
        }
        return false
    }

    fun selectSlot(slot: Int): Boolean {
        val inv = player.inventory
        if (slot in 0 until 9) {
            inv.selectedSlot = slot
            return true
        }
        return false
    }
}