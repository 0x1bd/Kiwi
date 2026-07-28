package org.kvxd.kiwi.task

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import org.kvxd.kiwi.bot.BotMemory
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.NO_ID
import org.kvxd.kiwi.nav.Navigator
import org.kvxd.kiwi.player
import org.kvxd.kiwi.world.LevelWorldView

class TaskContext(
    val navigator: Navigator,
    val memory: BotMemory,
    val view: LevelWorldView
) {

    var statusLine: String = ""

    fun log(message: String) {
        statusLine = message
    }

    fun playerPos(): BlockPos = player.blockPosition()

    fun inventory(): List<ItemStack> = (0..35).map { player.inventory.getItem(it) }

    fun count(itemId: Int): Int {
        if (itemId == NO_ID) return 0
        var total = 0
        for (slot in 0..35) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (Ids.itemOf(stack) == itemId) total += stack.count
        }
        return total
    }

    fun count(itemIds: IntArray): Int {
        if (itemIds.isEmpty()) return 0
        var total = 0
        for (slot in 0..35) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val id = Ids.itemOf(stack)
            for (candidate in itemIds) {
                if (candidate == id) {
                    total += stack.count
                    break
                }
            }
        }
        return total
    }

    fun has(itemId: Int, amount: Int = 1): Boolean = count(itemId) >= amount

    fun freeSlots(): Int = (0..35).count { player.inventory.getItem(it).isEmpty }
}
