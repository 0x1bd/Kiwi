package org.kvxd.baobab.control

import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import org.kvxd.baobab.client
import org.kvxd.baobab.pathing.calc.AStar
import org.kvxd.baobab.pathing.calc.MovementType
import org.kvxd.baobab.pathing.calc.Node
import org.kvxd.baobab.util.ClientMessenger
import kotlin.concurrent.thread
import kotlin.math.atan2
import kotlin.math.sqrt

object PathExecutor {

    private var path: List<Node>? = null
    private var index = 0

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

        if (path == null) {
            ClientMessenger.feedback("Calculating path to ${target.toShortString()}...")
        }

        thread {
            val result = AStar().calculate(start, target)

            client.execute {
                calculating = false
                if (result != null && result.isNotEmpty()) {
                    path = result
                    index = 0
                    InputController.active = true

                    if (result.size == 1 && result[0].pos == start) {
                        stop()
                        ClientMessenger.error("Path stuck (No valid moves).")
                    }
                } else {
                    stop()
                    client.player?.sendMessage(Text.literal("§cBaobab: No path found."), false)
                }
            }
        }
    }

    fun stop() {
        active = false
        path = null
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

        if (path == null) {
            repath()
            return
        }

        val player = client.player!!

        if (index >= path!!.size) {
            val distToGlobal = sqrt(player.blockPos.getSquaredDistance(globalGoal!!))

            if (distToGlobal > 2.0) {
                repath()
            } else {
                stop()
                ClientMessenger.feedback("Destination reached!")
            }
            return
        }

        val targetNode = path!![index]
        val targetPos = targetNode.toVec()
        val dist = player.squaredDistanceTo(targetPos.x, player.y, targetPos.z)

        if (dist < 0.6) {
            index++
            return
        }

        InputController.reset()
        InputController.forward = true

        val dx = targetPos.x - player.x
        val dz = targetPos.z - player.z
        val targetYaw = MathHelper.wrapDegrees((atan2(dz, dx) * 57.2957763671875).toFloat() - 90f)
        player.yaw = targetYaw

        if (targetNode.type == MovementType.JUMP) {
            InputController.jump = true
        } else if (player.horizontalCollision && player.isOnGround) {
            InputController.jump = true
        }
    }
}