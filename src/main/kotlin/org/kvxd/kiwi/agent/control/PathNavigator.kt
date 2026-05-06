package org.kvxd.kiwi.agent.control

import kotlinx.coroutines.*
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.*
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.coroutine.ClientDispatcher

object PathNavigator {

    private const val REPLAN_INITIAL = "initial"
    private const val REPLAN_FINISHED = "finished before goal"
    private const val REPLAN_OBSTRUCTED = "obstructed"
    private const val REPLAN_STUCK = "stuck"
    private const val REPLAN_PARTIAL_LOOKAHEAD = "partial lookahead"
    private const val PARTIAL_LOOKAHEAD_REMAINING = 8

    private val scope = CoroutineScope(SupervisorJob() + ClientDispatcher)

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

    suspend fun navigateToGoal(goal: Goal): NavigationResult {
        stop()
        currentGoal = goal
        active = true
        var result: NavigationResult = NavigationResult.Failed(PathFailureReason.NoLegalMoves)
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
                        result = NavigationResult.Reached
                        active = false
                        break
                    }

                    updateReplanRequest()
                    val replanReason = replanLimiter.consumeReady()
                    if (replanReason != null) {
                        val pathResult = calculatePath(goal, replanReason)
                        if (pathResult != null) {
                            result = NavigationResult.Failed(pathResult)
                            active = false
                            break
                        }
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

        return result
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
            } catch (e: Throwable) {
                active = false
                calculating = false
                DebugState.pathActive = false
                DebugState.pathCalculating = false
                DebugState.pathLastAction = "Navigation failed"
                DebugState.log("Navigation failed: ${e.message ?: e::class.simpleName}")
                ClientMessenger.error("Navigation failed: ${e.message ?: e::class.simpleName}")
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
            path.isEmpty -> replanLimiter.request(REPLAN_INITIAL)
            path.isFinished -> replanLimiter.request(REPLAN_FINISHED)
            PathValidator.isPathObstructed(path) -> replanLimiter.request(REPLAN_OBSTRUCTED)
            path.isPartial && path.remaining <= PARTIAL_LOOKAHEAD_REMAINING -> {
                replanLimiter.request(REPLAN_PARTIAL_LOOKAHEAD)
            }
        }
    }

    private suspend fun calculatePath(goal: Goal, reason: String): PathFailureReason? {
        val previousPath = path
        val keepFollowingCurrentPath = reason == REPLAN_PARTIAL_LOOKAHEAD &&
            !previousPath.isEmpty &&
            !previousPath.isFinished

        calculating = true
        if (!keepFollowingCurrentPath) {
            MovementController.stop()
        }
        DebugState.pathCalculating = true
        DebugState.pathLastAction = "Calculating..."
        DebugState.log("Calculating path ($reason)...")

        val result = if (keepFollowingCurrentPath) {
            calculateWhileFollowingCurrentPath(goal, previousPath)
        } else {
            PathPlanner.calculate(player.blockPosition(), goal)
        }

        calculating = false
        DebugState.pathCalculating = false
        if (result.status == PathStatus.UNREACHABLE || result.path == null || result.path.isEmpty) {
            val failure = result.reason ?: PathFailureReason.NoLegalMoves

            if (failure == PathFailureReason.OutsideLoadedChunks) {
                path = previousPath
                DebugState.pathLastAction = "Waiting for chunks"
                DebugState.log("Waiting for chunks before continuing")
                return null
            }

            if (keepFollowingCurrentPath) {
                path = previousPath
                DebugState.pathLastAction = "Continuing current partial path"
                DebugState.log("Could not refresh partial path yet: ${failure.describe()}")
                return null
            }

            DebugState.pathLastAction = failure.describe()
            DebugState.log("No path found: ${failure.describe()}")
            return failure
        }

        path = result.path
        stuckDetector.reset()
        InputOverride.capture()
        DebugState.pathSize = path.size
        DebugState.pathIndex = 0
        DebugState.pathRemaining = path.remaining
        DebugState.pathLastAction = if (result.status == PathStatus.PARTIAL) {
            "Partial path: ${result.reason?.describe() ?: "frontier"}"
        } else {
            "Path found"
        }
        DebugState.log("${DebugState.pathLastAction} (${path.size} nodes)")
        return null
    }

    private suspend fun calculateWhileFollowingCurrentPath(goal: Goal, followedPath: NodePath): PathResult {
        return coroutineScope {
            val start = player.blockPosition()
            val pendingPath = async {
                PathPlanner.calculate(start, goal)
            }

            while (pendingPath.isActive && path === followedPath && !path.isFinished) {
                if (pausedForManualControl) {
                    pauseMovement()
                } else {
                    followCurrentPath()
                    updateDebug()
                }
                delay(50)
            }

            if (path === followedPath && path.isFinished) {
                MovementController.stop()
            }

            pendingPath.await()
        }
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
            replanLimiter.request(REPLAN_STUCK)
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

sealed class NavigationResult {
    data object Reached : NavigationResult()
    data class Failed(val reason: PathFailureReason) : NavigationResult()
}