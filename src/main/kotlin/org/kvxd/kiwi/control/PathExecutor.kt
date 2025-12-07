package org.kvxd.kiwi.control

import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.calc.PathResult
import org.kvxd.kiwi.pathing.calc.RepathThread
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.PathProfiler

object PathExecutor {

    var path: NodePath = NodePath(emptyList())
        private set

    private var currentGoal: Goal? = null
    private var active = false
    private var calculating = false

    private var lastPos: Vec3d = Vec3d.ZERO
    private var stuckTicks = 0
    private const val STUCK_THRESHOLD_TICKS = 20
    private const val STUCK_DISTANCE_SQ = 0.0025

    fun setGoal(goal: Goal) {
        currentGoal = goal
        active = true
        stuckTicks = 0
        repath()
    }

    fun stop() {
        active = false
        calculating = false
        path = NodePath(emptyList())
        currentGoal = null
        InputOverride.deactivate()
        MovementController.stop()
        RotationManager.reset()
    }

    fun tick() {
        if (!active || client.player == null) return

        InputOverride.reset()

        if (calculating) {
            MovementController.stop()
            return
        }

        if (path.isEmpty) {
            repath()
            return
        }

        CollisionCache.clearCache()

        if (client.player!!.age % 4 == 0 && PathValidator.isPathObstructed(path)) {
            ClientMessenger.debug("Path obstructed! Repathing...")
            repath()
            return
        }

        var currNode = path.current() ?: run { finishCheck(); return }
        var executor = currNode.type.executor

        if (path.reachedCurrent(player.blockPos) && executor.isFinished(currNode)) {
            if (path.advance()) {
                currNode = path.current()!!
                executor = currNode.type.executor
                stuckTicks = 0
            } else {
                finishCheck()
                return
            }
        }

        val targetPos = currNode.toVec()
        if (checkDeviation(targetPos, executor.deviationThreshold)) return
        if (checkStuck(currNode)) return

        executor.execute(currNode, path)
        RotationManager.tick()
    }

    private fun checkDeviation(targetPos: Vec3d, threshold: Double): Boolean {
        val delta = player.entityPos.subtract(targetPos)

        if (delta.y < -ConfigManager.data.verticalDeviationThreshold) {
            ClientMessenger.debug("Vertical Deviation detected. Repathing...")
            repath()
            return true
        }

        val distSqXZ = delta.horizontalLengthSquared()
        if (distSqXZ > threshold * threshold) {
            ClientMessenger.debug("Horizontal Deviation detected. Repathing...")
            repath()
            return true
        }
        return false
    }

    private fun checkStuck(currentNode: Node): Boolean {
        if (!player.isOnGround && !player.isTouchingWater) return false
        if (currentNode.type == MovementType.MINE) return false

        val currentPos = player.entityPos
        if (currentPos.squaredDistanceTo(lastPos) < STUCK_DISTANCE_SQ) {
            stuckTicks++
        } else {
            stuckTicks = 0
            lastPos = currentPos
        }

        if (stuckTicks > STUCK_THRESHOLD_TICKS) {
            ClientMessenger.debug("Stuck detected. Repathing...")
            stuckTicks = 0
            repath()
            return true
        }
        return false
    }

    private fun repath() {
        val start = client.player?.blockPos ?: return
        val goal = currentGoal ?: return

        if (calculating) return

        calculating = true
        if (!path.isEmpty) ClientMessenger.debug("Recalculating path...")

        RepathThread(start, goal) { result ->
            client.execute { handlePathResult(result) }
        }.start()
    }

    private fun handlePathResult(result: PathResult) {
        calculating = false
        val success = result.path != null && !result.path.isEmpty

        if (ConfigManager.data.debugMode || !success) {
            PathProfiler.record(result, success)
        }

        if (success) {
            path = result.path
            InputOverride.activate()
            stuckTicks = 0

            if (path.size == 1 && path.current()?.pos == player.blockPos) {
                finishCheck()
            }
        } else {
            ClientMessenger.error("No path found.")
            stop()
        }
    }

    private fun finishCheck() {
        val goal = currentGoal ?: return
        if (goal.hasReached(player.blockPos)) {
            ClientMessenger.debug("Goal reached!")
            stop()
        } else {
            repath()
        }
    }
}