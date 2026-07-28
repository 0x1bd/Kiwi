package org.kvxd.kiwi.task.tasks

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.BreakSpeed
import org.kvxd.kiwi.control.ToolSelector
import org.kvxd.kiwi.harvest.HarvestCluster
import org.kvxd.kiwi.harvest.HarvestPlanner
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.level
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class HarvestTask(
    private val wantedItems: IntArray,
    private val amount: Int,
    private val label: String = wantedItems.joinToString("|") { Ids.itemName(it) }
) : AbstractTask("harvest") {

    private var committed: HarvestCluster? = null
    private var committedScore = Double.MAX_VALUE
    private var pendingPickup = false
    private var minedSincePickup = 0
    private var reselectCooldown = 0
    private var emptyScans = 0
    private var lastFailure: String? = null
    private var lastMined: BlockPos? = null

    override fun onStart(ctx: TaskContext) {
        committed = null
        committedScore = Double.MAX_VALUE
        pendingPickup = false
        minedSincePickup = 0
        reselectCooldown = 0
        emptyScans = 0
        lastFailure = null
        lastMined = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (ctx.count(wantedItems) >= amount) return TaskStatus.Success

        if (pendingPickup) {
            pendingPickup = false
            minedSincePickup = 0
            return TaskStatus.Delegate(PickupTask(wantedItems, amount))
        }

        val live = committed?.takeIf { hasLiveBlocks(ctx, it) }
        if (live == null && minedSincePickup > 0) {
            committed = null
            pendingPickup = true
            return TaskStatus.Running
        }

        val cluster = ensureCluster(ctx)
        if (cluster == null) {
            if (emptyScans++ < EMPTY_SCAN_GRACE) return TaskStatus.Running
            val reason = lastFailure ?: "no reachable source of $label within ${ConfigData.blockScanRadius} blocks"
            return TaskStatus.Failure(reason)
        }
        emptyScans = 0

        val next = nextBlock(ctx, cluster)
        if (next == null) {
            committed = null
            pendingPickup = minedSincePickup > 0
            return TaskStatus.Running
        }

        minedSincePickup++
        val stillNeeded = amount - ctx.count(wantedItems)
        if (minedSincePickup >= PICKUP_INTERVAL || minedSincePickup >= stillNeeded) pendingPickup = true

        ctx.log("mining $label at [${next.x},${next.y},${next.z}] (${cluster.size} in cluster)")
        return TaskStatus.Delegate(BreakBlockTask(next))
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (child is BreakBlockTask && status is TaskStatus.Success) {
            lastMined = child.target
        }
        if (child is BreakBlockTask && status is TaskStatus.Failure) {
            lastFailure = status.reason
            val cluster = committed
            if (cluster != null && nextBlock(ctx, cluster) == null) {
                committed = null
                pendingPickup = minedSincePickup > 0
            }
        }
    }

    private fun ensureCluster(ctx: TaskContext): HarvestCluster? {
        val stillNeeded = amount - ctx.count(wantedItems)
        val origin = ctx.playerPos()

        val current = committed
        if (current != null && hasLiveBlocks(ctx, current)) {
            committedScore = HarvestPlanner.score(current, origin, stillNeeded)
            if (reselectCooldown-- > 0) return current
            reselectCooldown = RESELECT_INTERVAL

            val challenger = scan(ctx, origin, stillNeeded) ?: return current
            if (current.contains(challenger.cluster.anchor)) return current
            if (challenger.score < committedScore - HarvestPlanner.SWITCH_MARGIN) {
                committed = challenger.cluster
                committedScore = challenger.score
                return challenger.cluster
            }
            return current
        }

        reselectCooldown = RESELECT_INTERVAL
        val option = scan(ctx, origin, stillNeeded) ?: return null
        committed = option.cluster
        committedScore = option.score
        return option.cluster
    }

    private fun scan(ctx: TaskContext, origin: BlockPos, stillNeeded: Int): org.kvxd.kiwi.harvest.HarvestOption? {
        val tools = ToolSelector.inventory()
        val sources = HarvestPlanner.sourceBlocks(wantedItems, tools)
        if (sources.isEmpty()) {
            lastFailure = missingToolMessage(tools)
            return null
        }

        val clusters = HarvestPlanner.findClusters(
            level = level,
            origin = origin,
            radius = ConfigData.blockScanRadius,
            sources = sources,
            memory = ctx.memory,
            breakTicksOf = { state, pos ->
                BreakSpeed.bestTicks(state, state.getDestroySpeed(level, pos), tools)
            }
        )
        return HarvestPlanner.best(clusters, origin, stillNeeded)
    }

    private fun missingToolMessage(tools: List<net.minecraft.world.item.ItemStack>): String {
        val missing = HarvestPlanner.missingTools(wantedItems, tools)
        return if (missing.isEmpty()) {
            "nothing in the world drops $label"
        } else {
            "need ${missing.first()} to harvest $label"
        }
    }

    private fun hasLiveBlocks(ctx: TaskContext, cluster: HarvestCluster): Boolean =
        nextBlock(ctx, cluster) != null

    private fun nextBlock(ctx: TaskContext, cluster: HarvestCluster): BlockPos? {
        val origin = ctx.playerPos()
        val sources = currentSources(ctx)
        return cluster.cheapest { pos ->
            if (ctx.memory.hasFailed(pos)) return@cheapest Double.POSITIVE_INFINITY
            val state = level.getBlockState(pos)
            if (state.isAir) return@cheapest Double.POSITIVE_INFINITY
            if (Ids.block(state.block) !in sources) return@cheapest Double.POSITIVE_INFINITY
            miningCost(ctx, origin, pos)
        }
    }

    private fun miningCost(ctx: TaskContext, origin: BlockPos, pos: BlockPos): Double {
        var cost = kotlin.math.sqrt(origin.distSqr(pos))

        val exposed = exposedFaces(ctx, pos)
        cost += if (exposed == 0) BURIED_PENALTY else (EXPOSURE_REFERENCE - minOf(exposed, EXPOSURE_REFERENCE)) * 1.5

        val previous = lastMined
        if (previous != null && pos.distSqr(previous) <= 3.001) cost -= CONTINUITY_BONUS

        return cost
    }

    private fun exposedFaces(ctx: TaskContext, pos: BlockPos): Int {
        var open = 0
        for (direction in net.minecraft.core.Direction.entries) {
            val neighbour = pos.relative(direction)
            if (!ctx.view.profile(neighbour.x, neighbour.y, neighbour.z).hasCollision) open++
        }
        return open
    }

    private var cachedSources: it.unimi.dsi.fastutil.ints.IntOpenHashSet? = null
    private var cachedSourcesTick = -1

    private fun currentSources(ctx: TaskContext): it.unimi.dsi.fastutil.ints.IntOpenHashSet {
        val tick = level.gameTime.toInt()
        val cached = cachedSources
        if (cached != null && cachedSourcesTick == tick) return cached
        val computed = HarvestPlanner.sourceBlocks(wantedItems, ToolSelector.inventory())
        cachedSources = computed
        cachedSourcesTick = tick
        return computed
    }

    override fun describe(): String {
        val cluster = committed
        return if (cluster == null) "harvest $label" else "harvest $label ($cluster)"
    }

    companion object {
        private const val PICKUP_INTERVAL = 4
        private const val RESELECT_INTERVAL = 3
        private const val EMPTY_SCAN_GRACE = 4
        private const val BURIED_PENALTY = 9.0
        private const val CONTINUITY_BONUS = 4.0
        private const val EXPOSURE_REFERENCE = 3
    }
}
