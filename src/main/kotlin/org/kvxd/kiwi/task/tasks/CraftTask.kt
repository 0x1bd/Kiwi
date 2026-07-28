package org.kvxd.kiwi.task.tasks

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.client
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.Containers
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.knowledge.CraftRecipe
import org.kvxd.kiwi.knowledge.CraftStation
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.GoalAdjacent
import org.kvxd.kiwi.player
import org.kvxd.kiwi.scan.BlockScan
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class CraftTask(private val recipe: CraftRecipe) : AbstractTask("craft") {

    private enum class Phase { STATION, OPEN, FILL, TAKE, CLOSE }

    private var phase = Phase.STATION
    private var tablePos: BlockPos? = null
    private var ticks = 0
    private var childFailure: String? = null
    private var placedTable = false

    private val gridWidth: Int get() = if (recipe.station == CraftStation.HAND) 2 else 3
    private val inventoryRange: IntRange get() = if (recipe.station == CraftStation.HAND) 9..45 else 10..45

    override fun onStart(ctx: TaskContext) {
        phase = Phase.STATION
        tablePos = null
        ticks = 0
        childFailure = null
        placedTable = false
        Controller.stopMoving()
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childFailure?.let {
            Containers.close()
            return TaskStatus.Failure(it)
        }
        if (ticks++ > MAX_TICKS) {
            Containers.close()
            return TaskStatus.Failure("crafting ${Ids.itemName(recipe.result)} timed out in $phase")
        }

        Controller.stopMoving()

        return when (phase) {
            Phase.STATION -> ensureStation(ctx)
            Phase.OPEN -> openGrid(ctx)
            Phase.FILL -> fillGrid(ctx)
            Phase.TAKE -> takeResult(ctx)
            Phase.CLOSE -> {
                Containers.close()
                TaskStatus.Success
            }
        }
    }

    private fun ensureStation(ctx: TaskContext): TaskStatus {
        if (recipe.station == CraftStation.HAND) {
            phase = Phase.OPEN
            return TaskStatus.Running
        }

        val existing = tablePos ?: nearbyTable(ctx)
        if (existing != null) {
            tablePos = existing
            if (!BlockBreaker.isInReach(existing)) {
                return TaskStatus.Delegate(NavigateTask(GoalAdjacent(existing, 3.0)))
            }
            phase = Phase.OPEN
            ticks = 0
            return TaskStatus.Running
        }

        if (placedTable) return TaskStatus.Failure("no crafting table available")
        placedTable = true

        val tableItem = Ids.item("crafting_table")
        if (ctx.count(tableItem) <= 0) return TaskStatus.Failure("need a crafting table")
        return TaskStatus.Delegate(PlaceBlockTask(tableItem))
    }

    private fun nearbyTable(ctx: TaskContext): BlockPos? =
        org.kvxd.kiwi.bot.Workstations.find(ctx.memory, Blocks.CRAFTING_TABLE, ctx.playerPos())

    private fun openGrid(ctx: TaskContext): TaskStatus {
        if (recipe.station == CraftStation.HAND) {
            if (player.containerMenu is InventoryMenu) {
                phase = Phase.FILL
                ticks = 0
            }
            return TaskStatus.Running
        }

        if (player.containerMenu is CraftingMenu) {
            phase = Phase.FILL
            ticks = 0
            return TaskStatus.Running
        }

        val pos = tablePos ?: return TaskStatus.Failure("lost the crafting table")
        val hitPoint = Vec3.atCenterOf(pos)
        LookController.lookAt(hitPoint)
        if (!LookController.isAimedAt(hitPoint, 8.0)) return TaskStatus.Running

        if (ticks % 5 == 0) {
            client.gameMode?.useItemOn(
                player,
                InteractionHand.MAIN_HAND,
                BlockHitResult(hitPoint, net.minecraft.core.Direction.UP, pos, false)
            )
        }
        return TaskStatus.Running
    }

    private fun fillGrid(ctx: TaskContext): TaskStatus {
        val menu = Containers.menu
        if (menu.slots.size < 10) return TaskStatus.Failure("unexpected crafting menu layout")

        for ((gridSlot, ingredient) in slotAssignments()) {
            var placed = 0
            while (placed < ingredient.count) {
                val source = Containers.findSlot(inventoryRange, ingredient.options)
                if (source == -1) {
                    dropCarried()
                    Containers.close()
                    return TaskStatus.Failure("missing ${ingredient.label} for ${Ids.itemName(recipe.result)}")
                }
                Containers.pickUp(source)
                Containers.placeOne(gridSlot)
                if (!Containers.carried().isEmpty) Containers.pickUp(source)
                placed++
            }
        }

        dropCarried()
        phase = Phase.TAKE
        ticks = 0
        return TaskStatus.Running
    }

    private fun slotAssignments(): List<Pair<Int, org.kvxd.kiwi.knowledge.Ingredient>> {
        if (recipe.isShaped && recipe.shapedSlots.isNotEmpty()) {
            return recipe.shapedSlots.mapIndexedNotNull { index, ingredient ->
                if (ingredient == null) return@mapIndexedNotNull null
                val row = index / recipe.width
                val column = index % recipe.width
                if (row >= gridWidth || column >= gridWidth) return@mapIndexedNotNull null
                (1 + row * gridWidth + column) to org.kvxd.kiwi.knowledge.Ingredient(1, ingredient.options, ingredient.label)
            }
        }

        val assignments = ArrayList<Pair<Int, org.kvxd.kiwi.knowledge.Ingredient>>()
        var slot = 1
        for (ingredient in recipe.ingredients) {
            repeat(ingredient.count) {
                if (slot <= gridWidth * gridWidth) {
                    assignments.add(slot to org.kvxd.kiwi.knowledge.Ingredient(1, ingredient.options, ingredient.label))
                    slot++
                }
            }
        }
        return assignments
    }

    private fun takeResult(ctx: TaskContext): TaskStatus {
        val result = Containers.menu.getSlot(0).item
        if (result.isEmpty) return TaskStatus.Running

        Containers.quickMove(0)
        phase = Phase.CLOSE
        return TaskStatus.Running
    }

    private fun dropCarried() {
        if (Containers.carried().isEmpty) return
        val empty = Containers.findEmptySlot(inventoryRange)
        if (empty != -1) Containers.pickUp(empty)
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (status is TaskStatus.Failure) {
            childFailure = status.reason
            return
        }
        if (child is PlaceBlockTask) {
            tablePos = child.placedAt
            child.placedAt?.let { org.kvxd.kiwi.bot.Workstations.remember(ctx.memory, Blocks.CRAFTING_TABLE, it) }
            ticks = 0
        }
    }

    override fun onStop(ctx: TaskContext, status: TaskStatus) {
        Containers.close()
    }

    override fun describe(): String = "craft ${Ids.itemName(recipe.result)} x${recipe.resultCount} [$phase]"

    companion object {
        private const val MAX_TICKS = 20 * 30
    }
}
