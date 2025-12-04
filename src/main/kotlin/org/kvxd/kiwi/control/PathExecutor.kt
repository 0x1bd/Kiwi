package org.kvxd.kiwi.control

import net.minecraft.util.math.Vec2f
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.AStar
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.goal.Goal
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.PathProfiler
import org.kvxd.kiwi.util.RotationUtils
import kotlin.concurrent.thread
import kotlin.math.abs

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

        val currNode = path.current()

        if (currNode == null) {
            finishCheck()
            return
        }

        val targetPos = currNode.toVec()
        val distSqXZ = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)

        val horizThreshold = ConfigManager.data.horizontalDeviationThreshold
        val vertThreshold = ConfigManager.data.verticalDeviationThreshold

        if (distSqXZ > horizThreshold || player.y < targetPos.y - vertThreshold) {
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

        // try to land centered on a block
        if (!isGrounded && !player.abilities.flying && node.type == MovementType.DROP) {
            val distSq = RotationUtils.getHorizontalDistanceSqr(player.entityPos, targetPos)

            if (distSq < 0.0025) {
                return
            }

            val localDistance = RotationUtils.getLocalVector(delta, player.yaw)
            val localVelocity = RotationUtils.getLocalVector(player.velocity, player.yaw)

            applyAirStrafe(localDistance, localVelocity)

            InputController.sprint = false
            return
        }

        player.yaw = targetYaw

        InputController.forward = true

        InputController.sprint = shouldSprint(isGrounded)

        if (node.type == MovementType.JUMP ||
            (player.horizontalCollision && player.isOnGround) ||
            (player.isTouchingWater && delta.y > 0)
        ) {
            InputController.jump = true
        }
    }

    private fun applyAirStrafe(localDistance: Vec2f, localVelocity: Vec2f) {
        val localForward = localDistance.y
        val localStrafe = localDistance.x
        val velForward = localVelocity.y
        val velStrafe = localVelocity.x

        if (abs(localForward) > 0.05) {
            if (localForward > 0) {
                if (velForward < 0.15) InputController.forward = true
            } else {
                if (velForward > -0.15) InputController.back = true
            }
        }

        if (abs(localStrafe) > 0.05) {
            if (localStrafe > 0) {
                if (velStrafe < 0.15) InputController.left = true
            } else {
                if (velStrafe > -0.15) InputController.right = true
            }
        }
    }

    private fun shouldSprint(grounded: Boolean): Boolean {
        if (!grounded) return false
        val next = path.peek(1) ?: return false
        return next.type.canSprint
    }
}