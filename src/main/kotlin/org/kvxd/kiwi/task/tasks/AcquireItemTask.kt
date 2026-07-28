package org.kvxd.kiwi.task.tasks

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.bot.BotLog
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.ToolSelector
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.level
import org.kvxd.kiwi.plan.Acquisition
import org.kvxd.kiwi.plan.AcquisitionInputs
import org.kvxd.kiwi.plan.AcquisitionPlan
import org.kvxd.kiwi.scan.BlockScan
import org.kvxd.kiwi.scan.ItemScan
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class AcquireItemTask(
    private val wanted: IntArray,
    private val amount: Int,
    private val label: String = Acquisition.label(wanted),
    private val ancestors: IntSet = IntOpenHashSet(),
    private val depth: Int = 0
) : AbstractTask("acquire") {

    private var target = 0
    private var steps = 0
    private var failures = 0
    private var lastPlan: AcquisitionPlan? = null
    private var lastFailure: String? = null

    private val blockedItems = IntOpenHashSet()
    private val blockedRecipes = HashSet<String>()
    private var harvestDisabled = false
    private var collectDisabled = false

    override fun onStart(ctx: TaskContext) {
        target = ctx.count(wanted) + amount
        steps = 0
        failures = 0
        lastFailure = null
        blockedItems.clear()
        blockedRecipes.clear()
        harvestDisabled = false
        collectDisabled = false
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (ctx.count(wanted) >= target) return TaskStatus.Success

        if (depth > MAX_DEPTH) return TaskStatus.Failure("$label: dependency chain too deep")
        if (failures > MAX_FAILURES) return give(ctx, "too many failed attempts")
        if (steps++ > MAX_STEPS) return give(ctx, "planner made no progress in $MAX_STEPS steps")

        val stillNeeded = target - ctx.count(wanted)
        val plan = Acquisition.plan(wanted, stillNeeded, inputs(ctx))
        lastPlan = plan

        ctx.log("$label x$stillNeeded: ${plan.reason}")
        BotLog.debug {
            "plan[$depth] $label need=$stillNeeded have=${ctx.count(wanted)} -> ${describePlan(plan)} " +
                "(cost=${"%.1f".format(plan.cost)}, ${plan.reason})" +
                if (blockedItems.isEmpty()) "" else " blocked=${blockedItems.toIntArray().joinToString(",") { Ids.itemName(it) }}"
        }

        return when (plan) {
            is AcquisitionPlan.Collect -> TaskStatus.Delegate(PickupTask(wanted, target))

            is AcquisitionPlan.Harvest -> TaskStatus.Delegate(HarvestTask(wanted, target, label))

            is AcquisitionPlan.Craft -> TaskStatus.Delegate(CraftTask(plan.recipe))

            is AcquisitionPlan.Smelt -> TaskStatus.Delegate(SmeltTask(plan.recipe))

            is AcquisitionPlan.Need -> TaskStatus.Delegate(
                AcquireItemTask(
                    wanted = plan.itemOptions,
                    amount = plan.amount,
                    label = plan.label,
                    ancestors = Acquisition.withAdded(ancestors, wanted),
                    depth = depth + 1
                )
            )

            is AcquisitionPlan.Impossible -> give(ctx, plan.reason)
        }
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (status is TaskStatus.Success) {
            if (child !is PickupTask) collectDisabled = false
            return
        }
        if (status !is TaskStatus.Failure) return

        failures++
        lastFailure = status.reason
        BotLog.debug { "plan[$depth] $label: ${child.name} failed: ${status.reason}" }

        when (val plan = lastPlan) {
            is AcquisitionPlan.Need -> for (option in plan.itemOptions) blockedItems.add(option)
            is AcquisitionPlan.Craft -> blockedRecipes.add(plan.recipe.id)
            is AcquisitionPlan.Smelt -> blockedRecipes.add(plan.recipe.id)
            is AcquisitionPlan.Harvest -> harvestDisabled = true
            is AcquisitionPlan.Collect -> collectDisabled = true
            else -> Unit
        }
    }

    private fun describePlan(plan: AcquisitionPlan): String = when (plan) {
        is AcquisitionPlan.Collect -> "collect"
        is AcquisitionPlan.Harvest -> "harvest"
        is AcquisitionPlan.Craft -> "craft ${plan.recipe.id}"
        is AcquisitionPlan.Smelt -> "smelt ${plan.recipe.id}"
        is AcquisitionPlan.Need -> "need ${plan.label} x${plan.amount}"
        is AcquisitionPlan.Impossible -> "impossible"
    }

    private fun give(ctx: TaskContext, reason: String): TaskStatus {
        val detail = lastFailure?.let { "$reason ($it)" } ?: reason
        BotLog.debug { "plan[$depth] $label gave up: $detail" }
        return TaskStatus.Failure("$label: $detail")
    }

    private fun inputs(ctx: TaskContext) = AcquisitionInputs(
        counts = { itemId -> ctx.count(itemId) },
        tools = ToolSelector.inventory(),
        nearbyDrops = { ids -> ItemScan.nearest(ids, DROP_SCAN_RADIUS) != null },
        harvestDistance = { sources ->
            val origin = ctx.playerPos()
            val hit = BlockScan.nearest(level, origin, ConfigData.blockScanRadius, HARVEST_SCAN_HEIGHT) { state ->
                sources.contains(Ids.block(state.block))
            }
            if (hit == null) Double.POSITIVE_INFINITY else weighted(origin, hit.pos)
        },
        hasCraftingTable = org.kvxd.kiwi.bot.Workstations.find(ctx.memory, Blocks.CRAFTING_TABLE, ctx.playerPos()) != null,
        hasFurnace = org.kvxd.kiwi.bot.Workstations.find(ctx.memory, Blocks.FURNACE, ctx.playerPos()) != null,
        avoid = Acquisition.withAdded(ancestors, blockedItems.toIntArray()),
        blockedRecipes = blockedRecipes,
        harvestDisabled = harvestDisabled,
        collectDisabled = collectDisabled
    )

    private fun weighted(origin: net.minecraft.core.BlockPos, target: net.minecraft.core.BlockPos): Double {
        val dx = (target.x - origin.x).toDouble()
        val dz = (target.z - origin.z).toDouble()
        val dy = (target.y - origin.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dz * dz) + kotlin.math.abs(dy) * VERTICAL_TRAVEL_WEIGHT
    }

    override fun describe(): String = "acquire $label x$amount"

    companion object {
        private const val MAX_DEPTH = 10
        private const val MAX_STEPS = 96
        private const val MAX_FAILURES = 6
        private const val DROP_SCAN_RADIUS = 16.0
        private const val HARVEST_SCAN_HEIGHT = 48
        private const val VERTICAL_TRAVEL_WEIGHT = 2.5
    }
}
