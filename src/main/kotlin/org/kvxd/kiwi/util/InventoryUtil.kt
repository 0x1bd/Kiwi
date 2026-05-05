package org.kvxd.kiwi.util

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.kvxd.kiwi.player

object InventoryUtil {

    val fullInventory: List<ItemStack>
        get() = (0..35).map { player.inventory.getItem(it) }

    val hotbarSlots: IntRange get() = 0..8

    val mainInventorySlots: IntRange get() = 9..35

    val selectedHotbarSlot: Int
        get() = player.inventory.selectedSlot

    fun countItem(predicate: (Item) -> Boolean): Int {
        return fullInventory.sumOf { stack ->
            if (predicate(stack.item)) stack.count else 0
        }
    }

    fun countItem(item: Class<out Item>): Int = fullInventory.sumOf { s -> if (item.isInstance(s.item)) s.count else 0 }

    fun countItem(item: Item): Int = fullInventory.sumOf { s -> if (s.item == item) s.count else 0 }

    fun hasItem(predicate: (Item) -> Boolean, count: Int = 1): Boolean {
        return countItem(predicate) >= count
    }

    fun hasItem(item: Item, count: Int = 1): Boolean = countItem(item) >= count

    fun hasItem(item: Class<out Item>, count: Int = 1): Boolean = countItem(item) >= count

    fun countPlaceableBlocks(): Int {
        return fullInventory.sumOf { stack ->
            if (stack.item is BlockItem) stack.count else 0
        }
    }

    fun hasPlaceableBlocks(count: Int = 1): Boolean = countPlaceableBlocks() >= count

    fun findSlot(predicate: (ItemStack) -> Boolean, range: IntRange = 0..35): Int {
        for (i in range) {
            if (predicate(player.inventory.getItem(i))) return i
        }
        return -1
    }

    fun findSlot(item: Item, range: IntRange = 0..35): Int {
        for (i in range) {
            if (player.inventory.getItem(i).item == item) return i
        }
        return -1
    }

    fun findSlot(item: Class<out Item>, range: IntRange = 0..35): Int {
        for (i in range) {
            if (item.isInstance(player.inventory.getItem(i).item)) return i
        }
        return -1
    }

    fun ensureInHotbar(predicate: (ItemStack) -> Boolean, exclude: ((ItemStack) -> Boolean)? = null): Boolean {
        val combined: (ItemStack) -> Boolean = { s ->
            if (s.isEmpty) false
            else if (exclude != null && exclude(s)) false
            else predicate(s)
        }
        val hotbarSlot = findSlot(combined, hotbarSlots)
        if (hotbarSlot != -1) {
            player.inventory.selectedSlot = hotbarSlot
            return true
        }

        val mainSlot = findSlot(combined, mainInventorySlots)
        if (mainSlot == -1) return false

        val emptyHotbarSlot = (0..8).firstOrNull { player.inventory.getItem(it).isEmpty }
        if (emptyHotbarSlot != null) {
            swapWithPickup(mainSlot, emptyHotbarSlot)
            player.inventory.selectedSlot = emptyHotbarSlot
            return true
        }
        return false
    }

    fun ensureInHotbar(item: Item): Boolean {
        return ensureInHotbar({ it.item == item }, null)
    }

    fun ensureInHotbar(item: Class<out Item>): Boolean {
        return ensureInHotbar({ item.isInstance(it.item) }, null)
    }

    private fun swapWithPickup(fromSlot: Int, toSlot: Int) {
        val inv = player.inventory
        val fromStack = inv.getItem(fromSlot).copy()
        val toStack = inv.getItem(toSlot).copy()
        inv.removeItem(fromSlot, fromStack.count)
        inv.removeItem(toSlot, toStack.count)
        inv.setItem(fromSlot, toStack)
        inv.setItem(toSlot, fromStack)
    }

    fun selectSlot(slot: Int): Boolean {
        if (slot in 0 until 9) {
            player.inventory.selectedSlot = slot
            return true
        }
        return false
    }

    fun selectSlot(block: (ItemStack) -> Boolean): Boolean {
        for (i in 0 until 9) {
            if (block(player.inventory.getItem(i))) {
                player.inventory.selectedSlot = i
                return true
            }
        }
        return false
    }

    fun findBestSlot(score: (ItemStack) -> Float): Int {
        val inv = player.inventory
        var bestSlot = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until 9) {
            val s = score(inv.getItem(i))
            if (s > bestScore) {
                bestScore = s
                bestSlot = i
            }
        }
        return bestSlot
    }

    fun getFreeSlots(): Int {
        return fullInventory.count { it.isEmpty }
    }

    fun getFreeHotbarSlots(): Int {
        return (0..8).count { player.inventory.getItem(it).isEmpty }
    }
}