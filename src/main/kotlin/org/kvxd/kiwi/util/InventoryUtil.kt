package org.kvxd.kiwi.util

import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import org.kvxd.kiwi.player

object InventoryUtil {

    fun selectSlot(block: (ItemStack) -> Boolean): Boolean {
        val inv = player.inventory

        if (inv.selectedStack.item is BlockItem) return true

        for (i in 0 until 9) {
            val stack = inv.getStack(i)
            if (block(stack)) {
                inv.selectedSlot = i
                return true
            }
        }
        return false
    }
}