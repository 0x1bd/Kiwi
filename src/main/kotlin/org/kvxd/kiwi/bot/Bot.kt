package org.kvxd.kiwi.bot

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.BreakSpeed
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.control.ToolSelector
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.NO_ID
import org.kvxd.kiwi.nav.Navigator
import org.kvxd.kiwi.path.BreakPolicy
import org.kvxd.kiwi.path.PathContext
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskRunner
import org.kvxd.kiwi.task.TaskStatus
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.world.LevelWorldView

object Bot {

    val memory = BotMemory()

    private val runner = TaskRunner()
    private var view: LevelWorldView? = null
    private var context: TaskContext? = null

    val navigator = Navigator(::pathPolicy)

    var lastResult: TaskStatus = TaskStatus.Success
    private set

    val isBusy: Boolean get() = runner.isRunning

    fun status(): String {
        if (!runner.isRunning) {
            return when (val result = lastResult) {
                is TaskStatus.Failure -> "Idle (last: failed - ${result.reason})"
                else -> "Idle"
            }
        }
        val trail = runner.trail()
        val root = trail.firstOrNull()?.name ?: "?"
        val leaf = trail.lastOrNull()?.describe() ?: "?"
        return "$root > $leaf"
    }

    fun statusLine(): String = context?.statusLine.orEmpty()

    fun taskTrail(): List<Task> = runner.trail()

    fun start(task: Task) {
        stop()
        BotLog.reset()
        BotLog.info("objective started: ${task.describe()}")
        val ctx = createContext()
        context = ctx
        lastResult = TaskStatus.Running
        Controller.engage()
        runner.start(ctx, task)
    }

    fun stop() {
        context?.let { runner.clear(it) }
        navigator.cancel()
        BlockBreaker.stop()
        Controller.release()
        LookController.reset()
        context = null
        view = null
    }

    fun tick() {
        if (client.player == null || client.level == null) {
            if (runner.isRunning) stop()
            return
        }
        if (!runner.isRunning) return

        val ctx = context ?: return
        ctx.view.invalidateAll()

        Controller.beginTick()
        val status = runner.tick(ctx)
        Controller.endTick()

        if (status !is TaskStatus.Running) {
            lastResult = status
            report(status)
            stop()
        }
    }

    private fun report(status: TaskStatus) {
        when (status) {
            is TaskStatus.Failure -> {
                BotLog.warn("objective failed: ${status.reason}")
                runCatching { ClientMessenger.error("Goal failed: ${status.reason}") }
                if (ConfigData.debugMode) {
                    runCatching { ClientMessenger.debug("Dump: ${DebugDump.write().fileName}") }
                }
            }

            is TaskStatus.Success -> {
                BotLog.info("objective complete")
                runCatching { ClientMessenger.feedback("Goal complete.") }
            }

            else -> Unit
        }
    }

    private fun createContext(): TaskContext {
        val liveView = LevelWorldView(org.kvxd.kiwi.level)
        view = liveView
        memory.clear()
        return TaskContext(navigator, memory, liveView)
    }

    fun pathPolicy(): PathContext {
        val tools: List<ItemStack> = runCatching { ToolSelector.inventory() }.getOrDefault(emptyList())
        val safeBlocks = ConfigData.safeToMineBlockTypes
        val buildBlocks = ConfigData.allowedBuildBlockTypes

        val protectedCells = LongOpenHashSet(memory.placedCells)

        return PathContext(
            view = view ?: LevelWorldView(org.kvxd.kiwi.level),
            breakPolicy = if (!ConfigData.allowBreak) BreakPolicy.NEVER else BreakPolicy.ANY,
            allowPlace = ConfigData.allowPillar,
            allowWater = ConfigData.allowWater,
            allowDiagonals = true,
            maxFallBlocks = ConfigData.maxFallHeight,
            placementBudget = if (ConfigData.allowPillar) countPlaceable() else 0,
            breakTicks = { profile ->
                BreakSpeed.bestTicks(profile.state, profile.destroySpeed, tools)
            },
            safeToBreak = { profile -> profile.state.block in safeBlocks },
            protectedCells = protectedCells,
            maxSearchIterations = ConfigData.pathSearchMaxIterations,
            placeable = { stack ->
                val item = stack.item
                item is BlockItem && item.block in buildBlocks
            }
        )
    }

    private fun countPlaceable(): Int {
        val allowed = ConfigData.allowedBuildBlockTypes
        var total = 0
        for (slot in 0..35) {
            val stack = client.player?.inventory?.getItem(slot) ?: continue
            if (stack.isEmpty) continue
            val item = stack.item
            if (item is BlockItem && item.block in allowed) total += stack.count
        }
        return total.coerceAtMost(org.kvxd.kiwi.path.MAX_TRACKED_PLACEMENTS)
    }

    fun itemId(name: String): Int = Ids.item(name)

    fun isKnownItem(name: String): Boolean = itemId(name) != NO_ID
}
