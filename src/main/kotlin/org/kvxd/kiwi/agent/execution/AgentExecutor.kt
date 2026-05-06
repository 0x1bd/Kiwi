package org.kvxd.kiwi.agent.execution

import org.kvxd.kiwi.agent.job.GoalAgenda
import org.kvxd.kiwi.agent.job.GoalFrame
import org.kvxd.kiwi.agent.planning.PlanningEngine
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.actions.BlockInfo
import org.kvxd.kiwi.agent.runtime.actions.collectItems
import org.kvxd.kiwi.agent.runtime.actions.craftItem
import org.kvxd.kiwi.agent.runtime.actions.mineBlock
import org.kvxd.kiwi.agent.runtime.actions.smeltItem
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.coroutine.waitClientTicks

class AgentExecutor(
    private val runtime: AgentRuntime
) {
    suspend fun run() {
        runtime.start()

        var decisionCount = 0
        while (!runtime.isRequestComplete()) {
            runtime.popCompletedGoals()
            val activeGoal = runtime.topGoal() ?: break

            if (++decisionCount > ConfigData.agentMaxPlanSteps) {
                throw AgentFailure("Planner exceeded ${ConfigData.agentMaxPlanSteps} decisions for ${runtime.request.itemId}")
            }
            if (runtime.failures > ConfigData.agentMaxFailures) {
                throw AgentFailure("Too many recoverable agent failures (${runtime.failures})")
            }

            runtime.phase = AgentPhase.PLANNING
            val decision = chooseDecision(activeGoal)
            runtime.currentPlan = decision.describe()
            runtime.currentPlanScore = decision.score
            runtime.updateDebug()

            dispatch(decision, activeGoal)
        }

        if (!runtime.isRequestComplete()) {
            throw AgentFailure("Stopped before reaching ${runtime.request.label}")
        }

        runtime.phase = AgentPhase.DONE
    }

    private suspend fun chooseDecision(activeGoal: GoalFrame): PlanningEngine.PlanDecision {
        return PlanningEngine.nextStep(
            PlanningEngine.PlanRequest(
                root = runtime.request,
                activeGoal = activeGoal,
                goals = runtime.goals.toList(),
                blockedCraftItems = runtime.agent.context.blockedCraftItems,
                blockedMineItems = runtime.agent.context.blockedMineItems,
                inventoryCounts = runtime.inventoryCounts(),
                playerPos = player.blockPosition(),
                environment = PlanningEngine.EnvironmentQuery(
                    findNearestDrop = runtime::findNearestDrop,
                    findNearestDropAny = runtime::findNearestDrop,
                    findNearestBlock = runtime::findNearestBlock,
                    findClosestBlock = runtime::findClosestBlock
                )
            )
        )
    }

    private suspend fun dispatch(decision: PlanningEngine.PlanDecision, activeGoal: GoalFrame) {
        when (decision) {
            is PlanningEngine.PlanDecision.AcquireItem -> pushGoal(decision)
            is PlanningEngine.PlanDecision.CollectDrop -> executeRecoverable(decision, activeGoal) {
                runtime.collectItems(decision.acceptedItemIds, amount = runtime.targetCountFor(activeGoal))
                runtime.popCompletedGoals()
            }
            is PlanningEngine.PlanDecision.MineBlock -> executeRecoverable(decision, activeGoal) {
                val blockInfo = BlockInfo(decision.block, dropId = decision.dropId)
                runtime.mineBlock(blockInfo, decision.targetPos)
                runtime.agent.context.minedPositions.add(decision.targetPos)
                runtime.agent.context.minedItemIds.add(decision.dropId)
                collectFreshDrops(activeGoal)
                runtime.popCompletedGoals()
            }
            is PlanningEngine.PlanDecision.CraftItem -> executeRecoverable(decision, activeGoal) {
                runtime.craftItem(decision.recipe)
                runtime.agent.context.craftableItemIds.add(decision.itemId)
                runtime.popCompletedGoals()
            }
            is PlanningEngine.PlanDecision.SmeltItem -> executeRecoverable(decision, activeGoal) {
                runtime.smeltItem(decision.recipe)
                runtime.popCompletedGoals()
            }
            is PlanningEngine.PlanDecision.NoPlan -> handleNoPlan(decision, activeGoal)
        }
    }

    private suspend fun pushGoal(decision: PlanningEngine.PlanDecision.AcquireItem) {
        when (runtime.pushGoal(decision.itemId, decision.acceptedItemIds, decision.amount, decision.reason, decision.displayName)) {
            GoalAgenda.PushResult.PUSHED,
            GoalAgenda.PushResult.EXTENDED -> return
            GoalAgenda.PushResult.DUPLICATE -> {
                runtime.failures++
                DebugState.log("Duplicate goal skipped: ${decision.acceptedItemIds.joinToString("|")}")
            }
            GoalAgenda.PushResult.BLOCKED -> {
                runtime.failures++
                DebugState.log("Blocked goal skipped: ${decision.acceptedItemIds.joinToString("|")}")
            }
            GoalAgenda.PushResult.INVALID -> {
                runtime.failures++
                DebugState.log("Invalid goal skipped: ${decision.itemId}")
            }
        }

        runtime.updateDebug()
        if (runtime.failures > ConfigData.agentMaxFailures) {
            throw AgentFailure("Too many planner dead ends while acquiring ${decision.itemId}")
        }
        waitClientTicks(1)
    }

    private suspend fun collectFreshDrops(activeGoal: GoalFrame) {
        if (runtime.remainingFor(activeGoal) <= 0) return

        try {
            runtime.collectItems(activeGoal.acceptedItemIds, amount = runtime.targetCountFor(activeGoal))
        } catch (e: AgentFailure) {
            DebugState.log("Post-mine pickup deferred for ${activeGoal.label}: ${e.message}")
        }
    }

    private suspend fun handleNoPlan(decision: PlanningEngine.PlanDecision.NoPlan, activeGoal: GoalFrame) {
        runtime.failures++
        runtime.phase = AgentPhase.RECOVERING
        runtime.updateDebug()
        DebugState.log("No plan for ${activeGoal.label}: ${decision.reason}")

        if (activeGoal.itemId == runtime.request.itemId && runtime.goals.size == 1) {
            throw AgentFailure("No available plan for ${activeGoal.itemId}")
        }

        runtime.agent.context.blockedCraftItems.add(activeGoal.itemId)
        runtime.agent.context.blockedMineItems.add(activeGoal.itemId)
        runtime.popGoal()
        waitClientTicks(1)
    }

    private suspend fun executeRecoverable(
        decision: PlanningEngine.PlanDecision,
        activeGoal: GoalFrame,
        block: suspend () -> Unit
    ) {
        try {
            block()
            runtime.popCompletedGoals()
        } catch (e: AgentFailure) {
            recover(decision, activeGoal, e)
        }
    }

    private suspend fun recover(
        decision: PlanningEngine.PlanDecision,
        activeGoal: GoalFrame,
        error: AgentFailure
    ) {
        runtime.failures++
        runtime.phase = AgentPhase.RECOVERING
        runtime.updateDebug()

        when (decision) {
            is PlanningEngine.PlanDecision.MineBlock -> {
                runtime.markFailedBlock(decision.block, decision.targetPos)
                DebugState.log("Mining failed at ${decision.targetPos}: ${error.message}")
            }
            is PlanningEngine.PlanDecision.CraftItem -> {
                runtime.agent.context.blockedCraftItems.add(decision.itemId)
                DebugState.log("Craft failed for ${decision.itemId}: ${error.message}")
            }
            is PlanningEngine.PlanDecision.SmeltItem -> {
                runtime.agent.context.blockedCraftItems.add(decision.itemId)
                DebugState.log("Smelt failed for ${decision.itemId}: ${error.message}")
            }
            is PlanningEngine.PlanDecision.CollectDrop -> {
                DebugState.log("Collect failed for ${decision.itemId}: ${error.message}")
                if (runtime.goals.isNotEmpty()) runtime.popGoal()
            }
            else -> Unit
        }

        if (runtime.failures > ConfigData.agentMaxFailures) {
            throw error
        }
        if (runtime.remainingFor(activeGoal) <= 0) {
            runtime.popCompletedGoals()
        }
        waitClientTicks(1)
    }

    private fun PlanningEngine.PlanDecision.describe(): String = when (this) {
        is PlanningEngine.PlanDecision.AcquireItem -> "Acquire ${displayName ?: acceptedItemIds.joinToString("|")}: $reason"
        is PlanningEngine.PlanDecision.CollectDrop -> "Collect ${acceptedItemIds.joinToString("|")}"
        is PlanningEngine.PlanDecision.MineBlock -> "Mine $blockId for ${acceptedItemIds.joinToString("|")}"
        is PlanningEngine.PlanDecision.CraftItem -> "Craft $itemId"
        is PlanningEngine.PlanDecision.SmeltItem -> "Smelt $itemId"
        is PlanningEngine.PlanDecision.NoPlan -> "No plan: $reason"
    }
}
