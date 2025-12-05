package org.kvxd.kiwi.control

import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.control.movement.mergeActionResult
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.calc.PathResult
import org.kvxd.kiwi.pathing.calc.RepathThread
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.PathProfiler
import kotlin.math.abs
import kotlin.math.sqrt

object PathExecutor {

    var path: NodePath = NodePath(emptyList())
        private set

    private var currentGoal: Goal? = null
    private var active = false
    private var calculating = false

    fun setGoal(goal: Goal) {
        currentGoal = goal
        active = true
        repath()
    }

    private fun repath() {
        val start = client.player?.blockPos ?: return
        val goal = currentGoal ?: return
        if (calculating) return
        calculating = true
        if (path.isEmpty) ClientMessenger.debug("Calculating path...")
        RepathThread(start, goal) { result -> handlePathResult(result) }.start()
    }

    private fun handlePathResult(result: PathResult) {
        calculating = false
        val success = result.path != null && !result.path.isEmpty
        if (ConfigManager.data.debugMode || !success) PathProfiler.record(result, success)

        if (success) {
            path = result.path
            InputOverride.activate()
            val first = path.current()
            val currentStart = client.player?.blockPos
            if (currentStart != null && first != null && first.pos == currentStart && path.size == 1) {
                finishCheck()
            }
        } else {
            stop()
            ClientMessenger.error("No path found.")
        }
    }

    fun stop() {
        active = false
        path = NodePath(emptyList())
        currentGoal = null
        InputOverride.deactivate()
        RotationManager.reset()
    }

    private fun finishCheck() {
        val goal = currentGoal ?: return
        if (goal.hasReached(player.blockPos)) {
            stop()
            ClientMessenger.debug("Goal reached!")
        } else {
            repath()
        }
    }

    fun tick() {
        if (!active || client.player == null) return

        InputOverride.reset()

        if (path.isEmpty || calculating) {
            repath()
            return
        }

        CollisionCache.clearCache()

        if (PathValidator.isPathObstructed(path)) {
            ClientMessenger.debug("Path obstructed! Repathing...")
            repath()
            return
        }

        var currNode = path.current() ?: run {
            finishCheck()
            return
        }

        var executor = currNode.type.executor

        if (path.reachedCurrent(player.blockPos)) {
            if (executor.isFinished(currNode)) {
                if (path.advance()) {
                    currNode = path.current()!!
                    executor = currNode.type.executor
                } else {
                    finishCheck()
                    return
                }
            }
        }

        val targetPos = currNode.toVec()

        val delta = player.entityPos.subtract(targetPos)

        val distSqXZ = delta.horizontalLengthSquared()

        val threshold = executor.deviationThreshold
        val maxDistSq = threshold * threshold

        if (delta.y < -ConfigManager.data.verticalDeviationThreshold) {
            ClientMessenger.debug("Deviated Y (Fallen ${String.format("%.2f", abs(delta.y))}m). Repathing...")
            repath()
            return
        }

        if (distSqXZ > maxDistSq) {
            val axis = if (abs(delta.x) > abs(delta.z)) "X" else "Z"
            val dist = sqrt(distSqXZ)

            ClientMessenger.debug("Deviated $axis (Dist: ${String.format("%.2f", dist)} > $threshold). Repathing...")
            repath()
            return
        }

        mergeActionResult(executor.execute(currNode, path)).forEach { action ->
            action.execute()
        }

        RotationManager.tick()
    }
}