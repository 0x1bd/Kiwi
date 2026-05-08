package org.kvxd.kiwi.agent.control

import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.calc.*
import org.kvxd.kiwi.agent.pathing.execute.MovementExecutorRegistry
import org.kvxd.kiwi.agent.pathing.goal.Goal
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.coroutine.ClientDispatcher
import org.kvxd.kiwi.util.coroutine.waitClientTicks
import java.util.Locale

object PathNavigator {

    private const val REPLAN_INITIAL = "initial"
    private const val REPLAN_FINISHED = "finished before goal"
    private const val REPLAN_OBSTRUCTED = "obstructed"
    private const val REPLAN_STUCK = "stuck"
    private const val REPLAN_PARTIAL_LOOKAHEAD = "partial lookahead"
    private const val PARTIAL_LOOKAHEAD_REMAINING = 2

    private val scope = CoroutineScope(SupervisorJob() + ClientDispatcher)

    var path: NodePath = NodePath(emptyList())
        private set

    private var currentGoal: Goal? = null
    private var active = false
    private var pausedForManualControl = false
    private var calculating = false
    private var navigationJob: Job? = null
    private var lastReplanRequestTraceKey: String? = null
    private var followTraceTicks = 0

    private val stuckDetector = StuckDetector()
    private val replanLimiter = ReplanLimiter()

    private data class PathCalculation(
        val start: BlockPos,
        val result: PathResult
    )

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
        tracePath { "goal set type=${DebugState.pathGoalType} target=${block(goal.getApproximateTarget())} ${pathSnapshot()}" }

        try {
            withContext(ClientDispatcher) {
                while (isActive) {
                    ensureActive()
                    if (pausedForManualControl) {
                        pauseMovement()
                        tracePath { "manual pause ${pathSnapshot()}" }
                        waitClientTicks(1)
                        continue
                    }

                    if (goal.hasReached(player.blockPosition())) {
                        markGoalReached("player block satisfies goal")
                        result = NavigationResult.Reached
                        active = false
                        break
                    }

                    if (path.isFinished && !path.isPartial) {
                        markGoalReached("complete path finished")
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

                    waitClientTicks(1)
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
            DebugState.pathCalculating = false
            DebugState.pathActive = false
            DebugState.log("Navigation ended")
            tracePath { "navigation ended result=$result ${pathSnapshot()}" }
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

    private fun markGoalReached(reason: String) {
        DebugState.pathGoalReached = true
        DebugState.pathLastAction = "Goal reached!"
        DebugState.log("Goal reached!")
        tracePath { "goal reached: $reason ${pathSnapshot()}" }
    }

    private fun updateReplanRequest() {
        when {
            path.isEmpty -> requestReplan(REPLAN_INITIAL, "path empty")
            path.isFinished -> {
                MovementController.stop()
                requestReplan(REPLAN_FINISHED, "path finished partial=${path.isPartial}")
            }
            else -> {
                val validation = PathValidator.validate(path)
                if (validation.obstructed) {
                    requestReplan(REPLAN_OBSTRUCTED, validation.reason)
                } else if (path.isPartial && path.remaining <= PARTIAL_LOOKAHEAD_REMAINING) {
                    requestReplan(REPLAN_PARTIAL_LOOKAHEAD, "remaining=${path.remaining} threshold=$PARTIAL_LOOKAHEAD_REMAINING")
                }
            }
        }
    }

    fun requestReplan(reason: String, detail: String = "") {
        replanLimiter.request(reason)

        val key = "$reason|$detail|${path.index}|${path.size}|${path.current()?.pos}|${path.previous()?.pos}"
        if (key != lastReplanRequestTraceKey) {
            lastReplanRequestTraceKey = key
            tracePath { "replan requested reason=$reason detail=$detail ${pathSnapshot()}" }
        }
    }

    private suspend fun calculatePath(goal: Goal, reason: String): PathFailureReason? {
        val previousPath = path
        val keepFollowingCurrentPath = reason == REPLAN_PARTIAL_LOOKAHEAD &&
            !previousPath.isEmpty &&
            !previousPath.isFinished
        lastReplanRequestTraceKey = null

        calculating = true
        if (!keepFollowingCurrentPath) {
            MovementController.stop()
        }
        DebugState.pathCalculating = true
        DebugState.pathLastAction = "Calculating..."
        DebugState.log("Calculating path ($reason)...")
        tracePath {
            "replan consumed reason=$reason keepFollowing=$keepFollowingCurrentPath start=${block(player.blockPosition())} " +
                "goal=${block(goal.getApproximateTarget())} previous=${pathSummary(previousPath)}"
        }

        val calculation = try {
            if (keepFollowingCurrentPath) {
                calculateWhileFollowingCurrentPath(goal, previousPath)
            } else {
                val start = player.blockPosition()
                PathCalculation(start, PathPlanner.calculate(start, goal))
            }
        } finally {
            calculating = false
            DebugState.pathCalculating = false
        }
        val result = calculation.result
        if (result.status == PathStatus.UNREACHABLE || result.path == null || result.path.isEmpty) {
            val failure = result.reason ?: PathFailureReason.NoLegalMoves

            if (failure == PathFailureReason.OutsideLoadedChunks) {
                path = previousPath
                DebugState.pathLastAction = "Waiting for chunks"
                DebugState.log("Waiting for chunks before continuing")
                tracePath { "path result outside chunks; keeping previous ${pathSummary(previousPath)} ${pathSnapshot()}" }
                return null
            }

            if (keepFollowingCurrentPath) {
                path = previousPath
                DebugState.pathLastAction = "Continuing current partial path"
                DebugState.log("Could not refresh partial path yet: ${failure.describe()}")
                tracePath { "partial refresh failed reason=${failure.describe()}; keeping previous ${pathSummary(previousPath)} ${pathSnapshot()}" }
                return null
            }

            DebugState.pathLastAction = failure.describe()
            DebugState.log("No path found: ${failure.describe()}")
            tracePath {
                "path failed status=${result.status} reason=${failure.describe()} visited=${result.nodesVisited} " +
                    "iterations=${result.iterations} timeMs=${fmt(result.timeComputedMs)} ${pathSnapshot()}"
            }
            return failure
        }

        if (keepFollowingCurrentPath && calculation.start != player.blockPosition()) {
            path = previousPath
            DebugState.pathLastAction = "Continuing current partial path"
            DebugState.log("Discarded stale partial refresh")
            tracePath {
                "partial refresh stale; discarding start=${block(calculation.start)} currentBlock=${block(player.blockPosition())} " +
                    "candidate=${pathSummary(result.path)} keeping=${pathSummary(previousPath)} ${pathSnapshot()}"
            }
            return null
        }

        path = result.path
        followTraceTicks = 0
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
        tracePath {
            "path accepted status=${result.status} reason=${result.reason?.describe() ?: "none"} " +
                "visited=${result.nodesVisited} iterations=${result.iterations} timeMs=${fmt(result.timeComputedMs)} " +
                pathSummary(path)
        }
        return null
    }

    private suspend fun calculateWhileFollowingCurrentPath(goal: Goal, followedPath: NodePath): PathCalculation {
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
                waitClientTicks(1)
            }

            if (path === followedPath && path.isFinished) {
                MovementController.stop()
            }

            PathCalculation(start, pendingPath.await())
        }
    }

    private fun followCurrentPath() {
        val currentNode = path.current() ?: run {
            MovementController.stop()
            tracePath { "follow stop: no current node ${pathSnapshot()}" }
            return
        }
        val executor = MovementExecutorRegistry.executorFor(currentNode.type)
        executor.execute(currentNode, path)
        RotationManager.tick()

        followTraceTicks++
        val reachedCurrent = PathProgress.hasReachedCurrent(path)
        val executorFinished = executor.isFinished(currentNode)
        if (followTraceTicks % 10 == 0 || reachedCurrent || !executorFinished) {
            tracePath {
                "follow reached=$reachedCurrent execFinished=$executorFinished executor=${executor::class.simpleName} " +
                    "stuckTicks=${stuckDetector.ticks} ${pathSnapshot()}"
            }
        }

        if (reachedCurrent && executorFinished) {
            val oldIndex = path.index
            path.advance()
            stuckDetector.reset()
            followTraceTicks = 0
            tracePath {
                "node advanced from=$oldIndex to=${path.index} finished=${path.isFinished} advancedNode=${node(currentNode)} ${pathSnapshot()}"
            }
            if (path.isFinished) {
                MovementController.stop()
            }
        }

        if (stuckDetector.tick(path, pausedForManualControl)) {
            requestReplan(REPLAN_STUCK, "stuckTicks=${stuckDetector.ticks}")
            stuckDetector.reset()
            DebugState.pathLastAction = "Stuck, repathing"
            DebugState.log("Stuck detected, repathing")
            tracePath { "stuck detected ${pathSnapshot()}" }
        }
    }

    private fun updateDebug() {
        DebugState.pathSize = path.size
        DebugState.pathIndex = path.index
        DebugState.pathRemaining = path.remaining
        DebugState.pathPartial = path.isPartial
        DebugState.pathStuckTicks = stuckDetector.ticks
    }

    private inline fun tracePath(message: () -> String) {
        if (ConfigData.debugMode) DebugState.tracePath(message())
    }

    private fun pathSnapshot(): String {
        return "player=${vec(player.position())} block=${block(player.blockPosition())} vel=${vec(player.deltaMovement)} " +
            "path[index=${path.index},size=${path.size},remaining=${path.remaining},partial=${path.isPartial}] " +
            "prev=${node(path.previous())} current=${node(path.current())} next=${node(path.next())} " +
            "input=${InputOverride.current()}"
    }

    private fun pathSummary(targetPath: NodePath): String {
        val first = targetPath[0]
        val last = targetPath.last()
        val current = targetPath.current()
        return "path[index=${targetPath.index},size=${targetPath.size},remaining=${targetPath.remaining},partial=${targetPath.isPartial}," +
            "first=${node(first)},current=${node(current)},last=${node(last)}]"
    }

    private fun node(node: Node?): String {
        if (node == null) return "null"
        val mining = if (node.miningBlocks.isEmpty()) "" else ",mine=${node.miningBlocks.size},mineCost=${fmt(node.miningCost)}"
        val pillarBlocks = if (node.pillarBlocks > 0 || node.type == MovementType.PILLAR) ",pillarBlocks=${node.pillarBlocks}" else ""
        return "${node.type}@${block(node.pos)}$mining$pillarBlocks"
    }

    private fun block(pos: net.minecraft.core.BlockPos?): String {
        if (pos == null) return "null"
        return "[${pos.x},${pos.y},${pos.z}]"
    }

    private fun vec(vec: net.minecraft.world.phys.Vec3): String {
        return "[${fmt(vec.x)},${fmt(vec.y)},${fmt(vec.z)}]"
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)
}

sealed class NavigationResult {
    data object Reached : NavigationResult()
    data class Failed(val reason: PathFailureReason) : NavigationResult()
}
