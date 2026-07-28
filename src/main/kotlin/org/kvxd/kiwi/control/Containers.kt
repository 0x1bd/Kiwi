package org.kvxd.kiwi.control

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import org.kvxd.kiwi.client
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.player

object Containers {

    val menu: AbstractContainerMenu get() = player.containerMenu

    fun pickUp(slot: Int) = click(slot, 0, ContainerInput.PICKUP)

    fun placeOne(slot: Int) = click(slot, 1, ContainerInput.PICKUP)

    fun quickMove(slot: Int) = click(slot, 0, ContainerInput.QUICK_MOVE)

    fun click(slot: Int, button: Int, input: ContainerInput) {
        val gameMode = client.gameMode ?: return
        gameMode.handleContainerInput(menu.containerId, slot, button, input, player)
    }

    fun carried() = menu.carried

    fun findSlot(range: IntRange, itemIds: IntArray): Int {
        for (slot in range) {
            if (slot >= menu.slots.size) break
            val stack = menu.getSlot(slot).item
            if (stack.isEmpty) continue
            val id = Ids.itemOf(stack)
            for (candidate in itemIds) if (candidate == id) return slot
        }
        return -1
    }

    fun findEmptySlot(range: IntRange): Int {
        for (slot in range) {
            if (slot >= menu.slots.size) break
            if (menu.getSlot(slot).item.isEmpty) return slot
        }
        return -1
    }

    fun close() {
        if (client.screen != null) player.closeContainer()
    }

    fun equip(accept: (net.minecraft.world.item.ItemStack) -> Boolean): Boolean {
        val slot = (0..35).firstOrNull { accept(player.inventory.getItem(it)) } ?: return false
        return equipInventorySlot(slot)
    }

    fun equipInventorySlot(inventorySlot: Int): Boolean {
        if (inventorySlot in 0..8) {
            player.inventory.selectedSlot = inventorySlot
            return true
        }

        val inventoryMenu = player.inventoryMenu
        if (player.containerMenu !== inventoryMenu) return false

        val hotbarIndex = (0..8).firstOrNull { player.inventory.getItem(it).isEmpty } ?: 0
        val gameMode = client.gameMode ?: return false
        gameMode.handleContainerInput(
            inventoryMenu.containerId,
            menuSlotOf(inventorySlot),
            hotbarIndex,
            ContainerInput.SWAP,
            player
        )
        player.inventory.selectedSlot = hotbarIndex
        return true
    }

    private fun menuSlotOf(inventorySlot: Int): Int =
        if (inventorySlot in 0..8) 36 + inventorySlot else inventorySlot
}
