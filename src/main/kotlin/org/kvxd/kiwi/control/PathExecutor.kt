package org.kvxd.kiwi.control

import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.client
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
import kotlin.math.min

object PathExecutor {

    var path: NodePath = NodePath(emptyList())
        private set

    private var currentGoal: Goal? = null
    private var active = false
    private var calculating = false
    private var pathingId = 0

    private var lastPos: Vec3d = Vec3d.ZERO
    private var stuckTicks = 0
    private const val STUCK_THRESHOLD_TICKS = 20
    private const val STUCK_DISTANCE_SQ = 0.0025

    fun setGoal(goal: Goal) {
        currentGoal = goal
        active = true
        stuckTicks = 0
        lastPos = player.entityPos
        repath()
    }

    fun stop() {
        pathingId++
        active = false
        calculating = false
        path = NodePath(emptyList())
        currentGoal = null
        stuckTicks = 0
        InputOverride.deactivate()
        MovementController.stop()
        RotationManager.reset()
    }

    fun tick() {
        if (!active) return

        if (!InputOverride.isActive) InputOverride.activate()
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

        if (player.age % 10 == 0) {
            if (PathValidator.isPathObstructed(path)) {
                ClientMessenger.debug("Path obstructed! Repathing...")
                repath()
                return
            }
        }

        var currNode = path.current() ?: run { finishCheck(); return }
        var executor = currNode.type.executor

        if (path.reachedCurrent(player.blockPos) && executor.isFinished(currNode)) {
            if (path.advance()) {
                currNode = path.current()!!
                executor = currNode.type.executor
                stuckTicks = 0
                lastPos = player.entityPos
            } else {
                finishCheck()
                return
            }
        }

        if (checkDeviation(path, executor.deviationThreshold)) return
        if (checkStuck(currNode)) return

        executor.execute(currNode, path)
        RotationManager.tick()
    }

    private fun checkDeviation(path: NodePath, threshold: Double): Boolean {
        val playerPos = player.entityPos
        val targetPos = path.current()!!.toVec()
        val prevNode = path.previous()

        if (path.current()?.type != MovementType.DROP && path.current()?.type != MovementType.MINE) {
            val minSegmentY = if (prevNode != null) {
                min(prevNode.pos.y.toDouble(), targetPos.y)
            } else {
                targetPos.y
            }

            if (playerPos.y < minSegmentY - 1.0) {
                ClientMessenger.debug("Vertical Deviation.")
                repath()
                return true
            }
        }

        val deviationSq: Double = if (prevNode != null) {
            getSquaredDistanceToSegment(playerPos, prevNode.toVec(), targetPos)
        } else {
            val dX = playerPos.x - targetPos.x
            val dZ = playerPos.z - targetPos.z
            dX * dX + dZ * dZ
        }

        if (deviationSq > threshold * threshold) {
            ClientMessenger.debug("Horizontal Deviation: $deviationSq")
            repath()
            return true
        }
        return false
    }

    private fun getSquaredDistanceToSegment(p: Vec3d, a: Vec3d, b: Vec3d): Double {
        val l2 = (b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z)
        if (l2 == 0.0) return (p.x - a.x) * (p.x - a.x) + (p.z - a.z) * (p.z - a.z)

        var t = ((p.x - a.x) * (b.x - a.x) + (p.z - a.z) * (b.z - a.z)) / l2
        t = t.coerceIn(0.0, 1.0)

        val projX = a.x + t * (b.x - a.x)
        val projZ = a.z + t * (b.z - a.z)

        val dX = p.x - projX
        val dZ = p.z - projZ

        return dX * dX + dZ * dZ
    }

    private fun checkStuck(currentNode: Node): Boolean {
        if (!player.isOnGround && !player.isTouchingWater) return false
        if (currentNode.type == MovementType.MINE || currentNode.type == MovementType.PILLAR) return false

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
        val start = player.blockPos ?: return
        val goal = currentGoal ?: return

        pathingId++
        val reqId = pathingId
        calculating = true

        RepathThread(start, goal) { result ->
            client.execute { handlePathResult(result, reqId) }
        }.start()
    }

    private fun handlePathResult(result: PathResult, reqId: Int) {
        if (reqId != pathingId || !active) return

        calculating = false
        val success = result.path != null && !result.path.isEmpty

        if (!success) {
            ClientMessenger.error("No path found.")
            stop()
            return
        }

        path = result.path
        stuckTicks = 0
        lastPos = player.entityPos
        InputOverride.activate()

        val firstNode = path.current() ?: return

        if (firstNode.pos == player.blockPos) {
            val next = path.peek(1)
            if (next != null && next.pos.y == firstNode.pos.y) {
                path.advance()
            } else if (path.size == 1) {
                finishCheck()
            }
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