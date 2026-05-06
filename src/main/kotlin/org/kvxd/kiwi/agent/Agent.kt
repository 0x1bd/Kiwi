package org.kvxd.kiwi.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.agent.control.NavigationResult
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.execution.AgentExecutor
import org.kvxd.kiwi.agent.job.AgentRequest
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.coroutine.ClientDispatcher

object Agent {

    var active: Boolean = false
        private set

    val status: String
        get() {
            if (!active) return "Idle"
            val label = currentTaskLabel ?: currentRequest?.label ?: return "Idle"
            return "Goal: $label | $phase"
        }

    val phase: String
        get() = runtime?.currentPhase ?: DebugState.agentPhase.ifBlank { "IDLE" }

    private var scope = CoroutineScope(SupervisorJob() + ClientDispatcher)
    private var currentJob: Job? = null
    private var currentRequest: AgentRequest? = null
    private var currentTaskLabel: String? = null

    internal var context = AgentMemory()
    internal var runtime: AgentRuntime? = null

    fun startItemGoal(itemId: String, amount: Int = 1) {
        stop()
        ensureAgentKnowledge()

        val request = AgentRequest(itemId, amount)
        val run = AgentRuntime(this, request)

        active = true
        currentRequest = request
        currentTaskLabel = request.label
        context = AgentMemory()
        runtime = run

        DebugState.agentObjective = request.itemId
        DebugState.agentObjectiveAmount = request.amount
        DebugState.agentPhase = "IDLE"
        DebugState.log("Goal started: ${request.label}")

        currentJob = scope.launch {
            try {
                AgentExecutor(run).run()
                ClientMessenger.feedback("Goal '${request.label}' complete!")
                DebugState.log("Goal '${request.label}' complete!")
            } catch (_: CancellationException) {
                // Planned stop.
            } catch (e: AgentFailure) {
                ClientMessenger.error("Goal failed: ${e.message}")
                DebugState.log("Goal failed: ${e.message}")
            } finally {
                finishRun(run)
            }
        }
    }

    fun startMovementGoal(goal: Goal, label: String) {
        stop()

        active = true
        currentRequest = null
        currentTaskLabel = label
        context = AgentMemory()
        runtime = null

        DebugState.agentObjective = label
        DebugState.agentObjectiveAmount = 1
        DebugState.agentPhase = "MOVING"
        DebugState.log("Movement goal started: $label")

        currentJob = scope.launch {
            try {
                when (val result = PathNavigator.navigateToGoal(goal)) {
                    NavigationResult.Reached -> Unit
                    is NavigationResult.Failed -> {
                        throw AgentFailure("Could not reach $label: ${result.reason.describe()}")
                    }
                }
                ClientMessenger.feedback("Movement goal '$label' complete!")
                DebugState.log("Movement goal '$label' complete!")
            } catch (_: CancellationException) {
                // Planned stop.
            } catch (e: AgentFailure) {
                ClientMessenger.error("Movement goal failed: ${e.message}")
                DebugState.log("Movement goal failed: ${e.message}")
            } catch (e: Throwable) {
                ClientMessenger.error("Movement goal crashed: ${e.message ?: e::class.simpleName}")
                DebugState.log("Movement goal crashed: ${e.message ?: e::class.simpleName}")
            } finally {
                finishRun()
            }
        }
    }

    private fun ensureAgentKnowledge() {
        if (HarvestDatabase.isLoaded) return

        try {
            HarvestDatabase.load()
            RecipeLookup.reloadRecipes()
        } catch (e: Exception) {
            ClientMessenger.error("Could not load harvest data: ${e.message}")
            DebugState.log("Harvest data load failed: ${e.message}")
        }
    }

    fun stop() {
        currentJob?.cancel()
        finishRun()
    }

    private fun finishRun(expectedRuntime: AgentRuntime? = null) {
        if (expectedRuntime != null && runtime !== expectedRuntime) return

        currentJob = null
        runtime?.cleanup()
        runtime = null
        currentRequest = null
        currentTaskLabel = null
        active = false
        PathNavigator.stop()
        InputOverride.release()
        RotationManager.reset()
        DebugState.reset()
    }

    internal fun countItemInInventory(itemId: String): Int =
        InventoryUtil.fullInventory.sumOf { stack ->
            if (stack.isEmpty) 0 else {
                val id = BuiltInRegistries.ITEM.getKey(stack.item).path
                if (id == itemId) stack.count else 0
            }
        }

    private val rememberedBlockPositions = mutableMapOf<Block, BlockPos>()

    internal fun rememberBlock(block: Block, pos: BlockPos) {
        rememberedBlockPositions[block] = pos
    }

    internal fun findRememberedBlock(block: Block): BlockPos? {
        val pos = rememberedBlockPositions[block] ?: return null
        return if (level.getBlockState(pos).`is`(block)) {
            pos
        } else {
            rememberedBlockPositions.remove(block)
            null
        }
    }
}
