package org.kvxd.kiwi.task.tasks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.FurnaceMenu
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.client
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.Containers
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.SmeltRecipe
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.GoalAdjacent
import org.kvxd.kiwi.player
import org.kvxd.kiwi.scan.BlockScan
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class SmeltTask(
    private val recipe: SmeltRecipe,
    private val batches: Int = 1
) : AbstractTask("smelt") {

    private enum class Phase { STATION, OPEN, LOAD, WAIT, TAKE, CLOSE }

    private var phase = Phase.STATION
    private var furnacePos: BlockPos? = null
    private var ticks = 0
    private var waitTicks = 0
    private var childFailure: String? = null
    private var placedFurnace = false
    private var startingResultCount = 0

    override fun onStart(ctx: TaskContext) {
        phase = Phase.STATION
        furnacePos = null
        ticks = 0
        waitTicks = 0
        childFailure = null
        placedFurnace = false
        startingResultCount = ctx.count(recipe.result)
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childFailure?.let {
            Containers.close()
            return TaskStatus.Failure(it)
        }
        if (ticks++ > MAX_TICKS) {
            Containers.close()
            return TaskStatus.Failure("smelting ${Ids.itemName(recipe.result)} timed out in $phase")
        }

        Controller.stopMoving()

        return when (phase) {
            Phase.STATION -> ensureFurnace(ctx)
            Phase.OPEN -> open(ctx)
            Phase.LOAD -> load(ctx)
            Phase.WAIT -> await(ctx)
            Phase.TAKE -> take(ctx)
            Phase.CLOSE -> {
                Containers.close()
                TaskStatus.Success
            }
        }
    }

    private fun ensureFurnace(ctx: TaskContext): TaskStatus {
        val existing = furnacePos ?: org.kvxd.kiwi.bot.Workstations.find(ctx.memory, Blocks.FURNACE, ctx.playerPos())
        if (existing != null) {
            furnacePos = existing
            if (!BlockBreaker.isInReach(existing)) {
                return TaskStatus.Delegate(NavigateTask(GoalAdjacent(existing, 3.0)))
            }
            phase = Phase.OPEN
            ticks = 0
            return TaskStatus.Running
        }

        if (placedFurnace) return TaskStatus.Failure("no furnace available")
        placedFurnace = true

        val furnaceItem = Ids.item("furnace")
        if (ctx.count(furnaceItem) <= 0) return TaskStatus.Failure("need a furnace")
        return TaskStatus.Delegate(PlaceBlockTask(furnaceItem))
    }

    private fun open(ctx: TaskContext): TaskStatus {
        if (player.containerMenu is FurnaceMenu) {
            phase = Phase.LOAD
            ticks = 0
            return TaskStatus.Running
        }

        val pos = furnacePos ?: return TaskStatus.Failure("lost the furnace")
        val hitPoint = Vec3.atCenterOf(pos)
        LookController.lookAt(hitPoint)
        if (!LookController.isAimedAt(hitPoint, 8.0)) return TaskStatus.Running

        if (ticks % 5 == 0) {
            client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(hitPoint, Direction.UP, pos, false))
        }
        return TaskStatus.Running
    }

    private fun load(ctx: TaskContext): TaskStatus {
        val inputSlot = Containers.findSlot(INVENTORY_RANGE, recipe.input.options)
        if (inputSlot == -1) {
            Containers.close()
            return TaskStatus.Failure("missing ${recipe.input.label} to smelt")
        }
        Containers.pickUp(inputSlot)
        Containers.pickUp(0)
        if (!Containers.carried().isEmpty) Containers.pickUp(inputSlot)

        if (Containers.menu.getSlot(1).item.isEmpty) {
            val fuelSlot = Containers.findSlot(INVENTORY_RANGE, FUEL_IDS)
            if (fuelSlot == -1) {
                Containers.close()
                return TaskStatus.Failure("no fuel to smelt ${Ids.itemName(recipe.result)}")
            }
            Containers.pickUp(fuelSlot)
            Containers.pickUp(1)
            if (!Containers.carried().isEmpty) Containers.pickUp(fuelSlot)
        }

        phase = Phase.WAIT
        waitTicks = 0
        return TaskStatus.Running
    }

    private fun await(ctx: TaskContext): TaskStatus {
        waitTicks++
        if (Containers.menu.getSlot(2).item.isEmpty) {
            if (waitTicks > MAX_WAIT_TICKS) {
                Containers.close()
                return TaskStatus.Failure("furnace produced nothing in ${MAX_WAIT_TICKS} ticks")
            }
            return TaskStatus.Running
        }
        phase = Phase.TAKE
        return TaskStatus.Running
    }

    private fun take(ctx: TaskContext): TaskStatus {
        Containers.quickMove(2)
        phase = Phase.CLOSE
        return TaskStatus.Running
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (status is TaskStatus.Failure) {
            childFailure = status.reason
            return
        }
        if (child is PlaceBlockTask) {
            furnacePos = child.placedAt
            child.placedAt?.let { org.kvxd.kiwi.bot.Workstations.remember(ctx.memory, Blocks.FURNACE, it) }
            ticks = 0
        }
    }

    override fun onStop(ctx: TaskContext, status: TaskStatus) {
        Containers.close()
    }

    override fun describe(): String = "smelt ${Ids.itemName(recipe.result)} [$phase]"

    companion object {
        private val INVENTORY_RANGE = 3..38
        private const val MAX_TICKS = 20 * 90
        private const val MAX_WAIT_TICKS = 20 * 60

        private val FUEL_IDS: IntArray by lazy {
            listOf("coal", "charcoal", "coal_block", "oak_planks", "spruce_planks", "birch_planks", "stick")
                .map { Ids.item(it) }
                .filter { it >= 0 }
                .toIntArray()
        }
    }
}
