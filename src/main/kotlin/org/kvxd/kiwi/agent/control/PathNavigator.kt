package org.kvxd.kiwi.agent.control

import kotlinx.coroutines.*
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.*
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.coroutine.ClientDispatcher

object PathNavigator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var path: NodePath = NodePath(emptyList())
        private set

    private var currentGoal: Goal? = null
    private var active = false
    private var pausedForManualControl = false
    private var calculating = false
    private var navigationJob: Job? = null

    private val stuckDetector = StuckDetector()
    private val replanLimiter = ReplanLimiter()

    fun setManualControlPaused(paused: Boolean) {
        pausedForManualControl = paused
        stuckDetector.reset()
    }

    suspend fun navigateToGoal(goal: Goal) {
        stop()
        currentGoal = goal
        active = true
        stuckDetector.reset()
        replanLimiter.reset()

        DebugState.pathActive = true
        DebugState.pathGoalType = goal::class.simpleName ?: "Goal"
        DebugState.pathGoalReached = false
        DebugState.pathLastAction = "Goal set"
        DebugState.log("Goal set: ${DebugState.pathGoalType}")

        try {
            withContext(ClientDispatcher) {
                while (isActive) {
                    ensureActive()
                    if (pausedForManualControl) {
                        pauseMovement()
                        delay(50)
                        continue
                    }

                    if (goal.hasReached(player.blockPosition())) {
                        markGoalReached()
                        active = false
                        break
                    }

                    updateReplanRequest()
                    val replanReason = replanLimiter.consumeReady()
                    if (replanReason != null && !calculatePath(goal, replanReason)) {
                        active = false
                        break
                    }

                    followCurrentPath()

                    updateDebug()

                    delay(50)
                }
            }
        } finally {
            MovementController.stop()
            InputOverride.clearActions()
            InputOverride.release()
            RotationManager.reset()
            active = false
            currentGoal = null
            path = NodePath(emptyList())
            calculating = false
            DebugState.pathActive = false
            DebugState.log("Navigation ended")
        }
    }

    fun stop() {
        active = false
        navigationJob?.cancel()
    }

    fun setGoal(goal: Goal, sessionKey: String = goal::class.simpleName ?: "Goal") {
        navigationJob?.cancel()
        navigationJob = scope.launch {
            try {
                navigateToGoal(goal)
            } catch (_: CancellationException) {
            }
        }
    }

    private fun pauseMovement() {
        InputOverride.clearMovement()
    }

    private fun markGoalReached() {
        DebugState.pathGoalReached = true
        DebugState.pathLastAction = "Goal reached!"
        DebugState.log("Goal reached!")
    }

    private fun updateReplanRequest() {
        when {
            path.isEmpty -> replanLimiter.request("initial")
            path.isFinished -> replanLimiter.request("finished before goal")
            PathValidator.isPathObstructed(path) -> replanLimiter.request("obstructed")
        }
    }

    private suspend fun calculatePath(goal: Goal, reason: String): Boolean {
        calculating = true
        path = NodePath(emptyList())
        MovementController.stop()
        DebugState.pathCalculating = true
        DebugState.pathLastAction = "Calculating..."
        DebugState.log("Calculating path ($reason)...")

        val result = PathPlanner.calculate(player.blockPosition(), goal)

        calculating = false
        DebugState.pathCalculating = false
        if (result.path == null || result.path.isEmpty) {
            DebugState.pathLastAction = "No path found"
            DebugState.log("No path found")
            return false
        }

        path = result.path
        stuckDetector.reset()
        InputOverride.capture()
        DebugState.pathSize = path.size
        DebugState.pathIndex = 0
        DebugState.pathRemaining = path.remaining
        DebugState.pathLastAction = if (result.isPartial) "Partial path" else "Path found"
        DebugState.log("${DebugState.pathLastAction} (${path.size} nodes)")
        return true
    }

    private fun followCurrentPath() {
        val currentNode = path.current() ?: return
        val executor = currentNode.type.executor
        executor.execute(currentNode, path)
        RotationManager.tick()

        if (PathProgress.hasReachedCurrent(path) && executor.isFinished(currentNode)) {
            path.advance()
            stuckDetector.reset()
        }

        if (stuckDetector.tick(path, pausedForManualControl)) {
            replanLimiter.request("stuck")
            stuckDetector.reset()
            DebugState.pathLastAction = "Stuck, repathing"
            DebugState.log("Stuck detected, repathing")
        }
    }

    private fun updateDebug() {
        DebugState.pathSize = path.size
        DebugState.pathIndex = path.index
        DebugState.pathRemaining = path.remaining
        DebugState.pathPartial = path.isPartial
        DebugState.pathStuckTicks = stuckDetector.ticks
    }
}