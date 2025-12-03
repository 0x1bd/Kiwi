package org.kvxd.kiwi.control

import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec2f
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.AStar
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.pathing.calc.PathResult
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import org.kvxd.kiwi.util.RotationUtils
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

object PathExecutor {

    var path: NodePath = NodePath(emptyList())
        private set

    private var globalGoal: BlockPos? = null
    private var active = false
    private var calculating = false

    fun computeAndSet(target: BlockPos) {
        globalGoal = target
        active = true
        repath()
    }

    private fun repath() {
        val start = client.player?.blockPos ?: return
        val target = globalGoal ?: return

        if (calculating) return
        calculating = true

        if (path.isEmpty && !ConfigManager.data.debugMode) {
            ClientMessenger.feedback("Calculating path to ${target.toShortString()}...")
        }

        thread {
            val result = AStar().calculate(start, target)

            client.execute {
                calculating = false

                if (result.path != null && !result.path.isEmpty) {
                    path = result.path
                    InputController.active = true

                    if (ConfigManager.data.debugMode) {
                        printDebugStats(result)
                    }

                    val first = path.current()
                    if (first != null && first.pos == start && path.size == 1) {
                        stop()
                        ClientMessenger.error("Path stuck (No valid moves).")
                    }

                } else {
                    stop()
                    ClientMessenger.error("No path found.")
                    if (ConfigManager.data.debugMode) {
                        printDebugStats(result)
                    }
                }
            }
        }
    }

    private fun printDebugStats(result: PathResult) {
        val nodesPerSec = if (result.timeComputedMs > 0)
            (result.nodesVisited / (result.timeComputedMs / 1000.0)).toInt()
        else 0

        val length = result.path?.size ?: 0

        ClientMessenger.send {
            text("[", Formatting.DARK_GRAY)
            text("Debug", Formatting.GREEN)
            text("] ", Formatting.DARK_GRAY)

            element("Time", String.format("%.2fms", result.timeComputedMs))
            separator()
            element("Visited", result.nodesVisited)
            separator()
            element("NPS", nodesPerSec, valueColor = Formatting.AQUA)
            separator()
            element("Len", length)
        }
    }

    fun stop() {
        active = false
        path = NodePath(emptyList())
        globalGoal = null
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
        val goal = globalGoal ?: return
        val distToGlobal = sqrt(player.blockPos.getSquaredDistance(goal))

        if (distToGlobal > ConfigManager.data.goalDistanceThreshold)
            repath()
        else {
            stop()
            ClientMessenger.feedback("Destination reached!")
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