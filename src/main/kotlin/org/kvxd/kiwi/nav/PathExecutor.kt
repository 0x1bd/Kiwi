package org.kvxd.kiwi.nav

import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.path.MoveBuffer
import org.kvxd.kiwi.path.MoveGenerator
import org.kvxd.kiwi.path.Path
import org.kvxd.kiwi.path.PathContext
import org.kvxd.kiwi.path.PathNode
import org.kvxd.kiwi.player
import org.kvxd.kiwi.world.LevelWorldView
import kotlin.math.abs

sealed interface FollowState {
    data object Following : FollowState
    data object Finished : FollowState
    data class Diverged(val reason: String) : FollowState
}

class PathExecutor(
    private val path: Path,
    private val plan: PathContext,
    private val view: LevelWorldView
) {
    var index: Int = 1
    private set

    private val ctx = ExecutionContext(plan, view)
    private val generator = MoveGenerator(plan)
    private val buffer = MoveBuffer()

    private var verifyCountdown = 0
    private var enteredCurrent = false

    val remaining: Int get() = (path.size - index).coerceAtLeast(0)

    val currentNode: PathNode? get() = path.node(index)

    val previousNode: PathNode? get() = path.node(index - 1)

    fun tick(): FollowState {
        if (path.isEmpty) return FollowState.Finished
        if (index >= path.size) return FollowState.Finished

        val from = path.node(index - 1) ?: return FollowState.Diverged("missing anchor node")
        val to = path.node(index) ?: return FollowState.Finished

        driftCheck(from, to)?.let { return FollowState.Diverged(it) }

        if (verifyCountdown <= 0) {
            verifyCountdown = VERIFY_INTERVAL
            if (!isStillPlannable(from, to)) {
                return FollowState.Diverged("move ${to.kind} into [${to.x},${to.y},${to.z}] is no longer legal")
            }
        } else {
            verifyCountdown--
        }

        val executor = MoveExecutors.forKind(to.kind)
        if (!enteredCurrent) {
            enteredCurrent = true
            ctx.ticksInMove = 0
            executor.onEnter(ctx, from, to)
        }
        ctx.ticksInMove++
        ctx.isFinalMove = index >= path.size - 1

        if (ctx.ticksInMove > executor.timeoutTicks(from, to)) {
            return FollowState.Diverged("move ${to.kind} timed out after ${ctx.ticksInMove} ticks")
        }

        return when (val outcome = executor.tick(ctx, from, to)) {
            MoveOutcome.Running -> FollowState.Following
            MoveOutcome.Done -> {
                advance()
                if (index >= path.size) FollowState.Finished else FollowState.Following
            }

            is MoveOutcome.Failed -> FollowState.Diverged(outcome.reason)
        }
    }

    fun stop() {
        Controller.stopMoving()
    }

    private fun advance() {
        index++
        enteredCurrent = false
        verifyCountdown = 0
    }

    private fun isStillPlannable(from: PathNode, to: PathNode): Boolean {
        generator.generate(from, buffer)
        for (i in 0 until buffer.size) {
            if (buffer.x[i] != to.x || buffer.y[i] != to.y || buffer.z[i] != to.z) continue
            if (buffer.kind[i] != to.kind) continue
            if (abs(buffer.feetY[i] - to.feetY) > 1.0E-6) continue
            return true
        }
        return to.kind.smoothable &&
            to.breaks.isEmpty() &&
            org.kvxd.kiwi.path.Corridor.isWalkable(view, from.x, from.z, to.x, to.z, to.feetY)
    }

    private fun driftCheck(from: PathNode, to: PathNode): String? {
        val distanceToSegment = distanceToSegmentSq(from, to)
        if (distanceToSegment > MAX_DRIFT_SQ) {
            return "drifted ${"%.2f".format(Math.sqrt(distanceToSegment))} blocks off the planned segment"
        }
        return null
    }

    private fun distanceToSegmentSq(from: PathNode, to: PathNode): Double {
        val ax = from.x + 0.5
        val az = from.z + 0.5
        val bx = to.x + 0.5
        val bz = to.z + 0.5
        val px = player.x
        val pz = player.z

        val dx = bx - ax
        val dz = bz - az
        val lengthSq = dx * dx + dz * dz
        val t = if (lengthSq < 1.0E-9) 0.0 else (((px - ax) * dx + (pz - az) * dz) / lengthSq).coerceIn(0.0, 1.0)
        val cx = ax + dx * t
        val cz = az + dz * t
        val ex = px - cx
        val ez = pz - cz
        return ex * ex + ez * ez
    }

    companion object {
        private const val VERIFY_INTERVAL = 4
        private const val MAX_DRIFT = 2.6
        private const val MAX_DRIFT_SQ = MAX_DRIFT * MAX_DRIFT
    }
}
