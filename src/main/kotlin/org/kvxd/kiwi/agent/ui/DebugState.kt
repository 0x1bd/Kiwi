package org.kvxd.kiwi.agent.ui

import net.minecraft.core.BlockPos

object DebugState {

    var pathActive = false
    var pathCalculating = false
    var pathSize = 0
    var pathIndex = 0
    var pathRemaining = 0
    var pathPartial = false
    var pathGoalType = ""
    var pathStuckTicks = 0
    var pathLastAction = ""
    var pathLastActionResult = ""
    var pathGoalReached = false

    var agentPhase = ""
    var agentObjective = ""
    var agentObjectiveAmount = 0
    var agentMineTarget: BlockPos? = null
    var agentMineBlockId = ""
    var agentMineRemaining = 0
    var raycastLabel = ""
    var agentStuckTicks = 0
    var agentGoalCount = 0
    var agentGoalTop = ""
    var agentPlanFailures = 0
    var agentKnownBlocks = 0

    var lastMessageTime = 0L
    var recentMessages = mutableListOf<String>()
    private const val MAX_RECENT = 8

    fun reset() {
        pathActive = false
        pathCalculating = false
        pathSize = 0
        pathIndex = 0
        pathRemaining = 0
        pathPartial = false
        pathGoalType = ""
        pathStuckTicks = 0
        pathLastAction = ""
        pathLastActionResult = ""
        pathGoalReached = false
        agentPhase = ""
        agentObjective = ""
        agentObjectiveAmount = 0
        agentMineTarget = null
        agentMineBlockId = ""
        agentMineRemaining = 0
        raycastLabel = ""
        agentStuckTicks = 0
        agentGoalCount = 0
        agentGoalTop = ""
        agentPlanFailures = 0
        agentKnownBlocks = 0
        recentMessages.clear()
    }

    fun log(message: String) {
        recentMessages.add(message)
        if (recentMessages.size > MAX_RECENT) {
            recentMessages.removeAt(0)
        }
        lastMessageTime = System.currentTimeMillis()
    }
}
