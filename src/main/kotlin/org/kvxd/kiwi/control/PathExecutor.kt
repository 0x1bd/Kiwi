package org.kvxd.kiwi.control

import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.calc.PathResult
import org.kvxd.kiwi.pathing.calc.RepathThread
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.PathProfiler
import org.kvxd.kiwi.util.RotationUtils

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
            InputController.active = true
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
        InputController.active = false
        InputController.reset()
        RotationManager.reset()
    }

    private fun finishCheck() {
        val goal = currentGoal ?: return
        if (goal.hasReached(player.blockPos)) {
            stop()
            ClientMessenger.feedback("Goal reached!")
        } else {
            repath()
        }
    }

    fun tick() {
        if (!active || client.player == null) return

        if (calculating) {
            InputController.reset()
            return
        }

        if (path.isEmpty) {
            repath()
            return
        }

        CollisionCache.clearCache()

        if (PathValidator.isPathObstructed(path)) {
            ClientMessenger.debug("Path obstructed! Repathing...")
            repath()
            return
        }

        val currNode = path.current() ?: run {
            finishCheck()
            return
        }

        val executor = currNode.type.executor

        val targetPos = currNode.toVec()
        val distSqXZ = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)

        if (distSqXZ > executor.deviationThreshold ||
            player.y < targetPos.y - ConfigManager.data.verticalDeviationThreshold
        ) {
            ClientMessenger.debug("Deviated. Repathing...")
            repath()
            return
        }

        if (path.reachedCurrent(player.blockPos)) {
            if (executor.isFinished(currNode)) {
                if (!path.advance()) finishCheck()
                return
            }
        }

        executor.execute(currNode, path)

        RotationManager.tick()
    }
}