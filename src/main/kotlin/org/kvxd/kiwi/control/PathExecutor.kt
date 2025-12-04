package org.kvxd.kiwi.control

import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.AStar
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.pathing.move.Physics
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.PathProfiler
import org.kvxd.kiwi.util.RotationUtils
import kotlin.concurrent.thread

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

        if (path.isEmpty && !ConfigManager.data.debugMode) {
            ClientMessenger.feedback("Calculating path...")
        }

        thread {
            val result = AStar().calculate(start, goal)

            client.execute {
                calculating = false

                val success = result.path != null && !result.path.isEmpty
                if (ConfigManager.data.debugMode || !success) {
                    PathProfiler.record(result, success)
                }

                if (success) {
                    path = result.path
                    InputController.active = true

                    val first = path.current()
                    if (first != null && first.pos == start && path.size == 1) {
                        finishCheck()
                    }
                } else {
                    stop()
                    ClientMessenger.error("No path found to destination.")
                }
            }
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

        Physics.clearCache()

        if (PathValidator.isPathObstructed(path)) {
            if (ConfigManager.data.debugMode) ClientMessenger.feedback("Path obstructed! Repathing...")
            repath()
            return
        }

        val currNode = path.current()

        if (currNode == null) {
            finishCheck()
            return
        }

        val targetPos = currNode.toVec()
        val distSqXZ = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)

        if (distSqXZ > ConfigManager.data.horizontalDeviationThreshold ||
            player.y < targetPos.y - ConfigManager.data.verticalDeviationThreshold
        ) {
            if (ConfigManager.data.debugMode) ClientMessenger.feedback("Deviated. Repathing...")
            repath()
            return
        }

        if (path.reachedCurrent(player.blockPos)) {
            if (!path.advance()) {
                finishCheck()
            }
            return
        }

        moveTowardNode(currNode)
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

    private fun moveTowardNode(node: Node) {
        InputController.reset()

        val isGrounded = player.isOnGround || player.isTouchingWater
        val targetPos = node.toVec()
        val delta = targetPos.subtract(player.entityPos)
        val targetYaw = RotationUtils.getLookYaw(player.entityPos, targetPos)

        if (!isGrounded && !player.abilities.flying && node.type == MovementType.DROP) {
            val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)
            if (distSq < 0.0025) return

            MovementController.applyAirStrafe(player, targetPos)

            InputController.sprint = false
            RotationManager.reset()
            return
        }

        RotationManager.setTarget(targetYaw, 0f)

        if (!ConfigManager.data.freelook) {
            player.yaw = targetYaw
        }

        MovementController.applyControls(targetYaw, player.yaw)

        InputController.sprint = MovementController.shouldSprint(player, path)

        if (node.type == MovementType.JUMP || (player.isTouchingWater && delta.y > 0)) {
            InputController.jump = true
        }
    }
}