package org.kvxd.kiwi.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.execution.AgentExecutor
import org.kvxd.kiwi.agent.job.AgentRequest
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.level
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.coroutine.ClientDispatcher

object Agent {

    var active: Boolean = false
        private set

    val status: String
        get() {
            val request = currentRequest ?: return "Idle"
            return if (!active) "Idle" else "Goal: ${request.label} | $phase"
        }

    val phase: String
        get() = runtime?.currentPhase ?: "IDLE"

    private var scope = CoroutineScope(SupervisorJob() + ClientDispatcher)
    private var currentJob: Job? = null
    private var currentRequest: AgentRequest? = null

    internal var context = AgentMemory()
    internal var runtime: AgentRuntime? = null

    fun startItemGoal(itemId: String, amount: Int = 1) {
        stop()
        ensureAgentKnowledge()

        val request = AgentRequest(itemId, amount)
        val run = AgentRuntime(this, request)

        active = true
        currentRequest = request
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

    private val rememberedBlockPositions = mutableMapOf<String, BlockPos>()

    internal fun rememberBlock(blockId: String, pos: BlockPos) {
        rememberedBlockPositions[blockId] = pos
    }

    internal fun findRememberedBlock(blockId: String): BlockPos? {
        val pos = rememberedBlockPositions[blockId] ?: return null
        val currentId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).path
        return if (currentId == blockId) {
            pos
        } else {
            rememberedBlockPositions.remove(blockId)
            null
        }
    }
}
