package org.kvxd.kiwi.control

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import org.kvxd.kiwi.client
import org.kvxd.kiwi.pathing.calc.AStar
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.ClientMessenger
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.atan2
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

        if (path.isEmpty) {
            ClientMessenger.feedback("Calculating path to ${target.toShortString()}...")
        }

        thread {
            val result = AStar().calculate(start, target)

            client.execute {
                calculating = false

                if (result != null && !result.isEmpty) {
                    path = result
                    InputController.active = true

                    val first = path.current()
                    if (first != null && first.pos == start && path.size == 1) {
                        stop()
                        ClientMessenger.error("Path stuck (No valid moves).")
                    }

                } else {
                    stop()
                    ClientMessenger.error("No path found.")
                }
            }
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

        if (distToGlobal > 2.0) repath()
        else {
            stop()
            ClientMessenger.feedback("Destination reached!")
        }
    }

    private fun moveTowardNode(node: Node) {
        InputController.reset()

        val isGrounded = player.isOnGround || player.isTouchingWater

        InputController.forward = true
        InputController.sprint = shouldSprint(isGrounded)

        val vec = node.toVec()
        val dx = vec.x - player.x
        val dz = vec.z - player.z
        val yaw = MathHelper.wrapDegrees(
            ((atan2(dz, dx) * 180.0 / PI).toFloat() - 90f)
        )
        player.yaw = yaw

        if (node.type == MovementType.JUMP ||
            (player.horizontalCollision && player.isOnGround) ||
            (player.isTouchingWater && vec.y > player.y)) {
            InputController.jump = true
        }
    }

    private fun shouldSprint(grounded: Boolean): Boolean {
        if (!grounded) return false
        val next = path.peek(1) ?: return false

        return next.type.canSprint
    }
}