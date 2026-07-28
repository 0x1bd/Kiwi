package org.kvxd.kiwi.nav

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.BlockPlacer
import org.kvxd.kiwi.control.BreakProgress
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.control.PlaceResult
import org.kvxd.kiwi.control.Steering
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.MoveKind
import org.kvxd.kiwi.path.PathNode
import org.kvxd.kiwi.player
import org.kvxd.kiwi.world.PlayerBox
import kotlin.math.abs
import kotlin.math.sqrt

private const val ARRIVE_RADIUS = 0.32
private const val ARRIVE_RADIUS_SQ = ARRIVE_RADIUS * ARRIVE_RADIUS
private const val ALIGN_RADIUS_SQ = 0.16 * 0.16

private const val MIN_STEER_SQ = 0.04

private const val LEDGE_PUSH_SQ = 0.36

private const val LEDGE_DROP_EPSILON = 0.3

private const val LEDGE_OVERSHOOT = 0.9

private const val PASSED_RADIUS_SQ = 0.9 * 0.9

private fun horizontalDistanceSq(target: Vec3): Double {
    val dx = player.x - target.x
    val dz = player.z - target.z
    return dx * dx + dz * dz
}

private fun segmentDirection(from: PathNode, to: PathNode): Vec3 {
    val dx = (to.x - from.x).toDouble()
    val dz = (to.z - from.z).toDouble()
    if (dx * dx + dz * dz < 1.0E-9) return Vec3.ZERO
    return Vec3(dx, 0.0, dz).normalize()
}

private fun steerTowards(target: Vec3, sprint: Boolean, brake: Boolean = false) {
    LookController.faceHorizontally(player.position(), target)
    Steering.driveTo(target, sprint, brake)
}

private fun steerOverLedge(ctx: ExecutionContext, from: PathNode, to: PathNode, sprint: Boolean) {
    val target = nodeCenter(to)
    val segment = segmentDirection(from, to)
    val direct = Vec3(target.x - player.x, 0.0, target.z - player.z)
    val awaitingDrop = player.onGround() && player.y > to.feetY + LEDGE_DROP_EPSILON

    if (awaitingDrop && segment.lengthSqr() > 0.0 && direct.lengthSqr() < LEDGE_PUSH_SQ) {
        val beyond = Vec3(target.x + segment.x * LEDGE_OVERSHOOT, target.y, target.z + segment.z * LEDGE_OVERSHOOT)
        LookController.faceHorizontally(player.position(), beyond)
        Steering.driveTo(beyond, sprint = false, brake = false)
        return
    }

    if (ctx.isFinalMove) {
        Steering.driveTo(target, sprint, brake = true)
    } else {
        Steering.driveSegment(nodeCenter(from), target, sprint)
    }
}

private fun clearPlannedBreaks(node: PathNode): MoveOutcome? {
    for (cell in node.breaks) {
        val pos = BlockPos.of(cell)
        if (level.getBlockState(pos).isAir) continue

        Controller.stopMoving()
        return when (BlockBreaker.tick(pos)) {
            BreakProgress.DONE -> MoveOutcome.Running
            BreakProgress.BREAKING, BreakProgress.APPROACHING -> MoveOutcome.Running
            BreakProgress.IMPOSSIBLE -> MoveOutcome.Failed("cannot break planned block at $pos")
        }
    }
    return null
}

object WalkExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.WALK, MoveKind.SWIM)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        clearPlannedBreaks(to)?.let { return it }

        val target = nodeCenter(to)
        val distanceSq = horizontalDistanceSq(target)
        val passed = Steering.progressAlong(nodeCenter(from), target) >= 1.0

        if ((distanceSq <= ARRIVE_RADIUS_SQ || (passed && distanceSq <= PASSED_RADIUS_SQ)) &&
            verticallySettled(to)
        ) return MoveOutcome.Done

        val sprint = to.kind.allowsSprint && distanceSq > 1.6 && player.foodData.foodLevel > 6
        steerOverLedge(ctx, from, to, sprint)

        if (to.kind == MoveKind.SWIM && (player.isUnderWater || target.y > player.y + 0.2)) {
            Controller.input().jump = true
        }

        if (to.kind == MoveKind.WALK && player.horizontalCollision && player.onGround()) {
            val rise = to.feetY - player.y
            if (rise > PlayerBox.STEP_HEIGHT && rise <= PlayerBox.JUMP_HEIGHT) Controller.input().jump = true
        }

        return MoveOutcome.Running
    }

    private fun verticallySettled(to: PathNode): Boolean {
        if (to.kind == MoveKind.SWIM) return abs(player.y - to.feetY) <= 0.9
        if (abs(player.y - to.feetY) > 0.55) return false
        return player.onGround() || player.isInWater
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val distance = sqrt(dx * dx + dz * dz)
        return 40 + (distance * 12).toInt() + to.breaks.size * 120
    }
}

object JumpExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.JUMP)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        clearPlannedBreaks(to)?.let { return it }

        val target = nodeCenter(to)
        val distanceSq = horizontalDistanceSq(target)

        if (player.y >= to.feetY - 0.05 && distanceSq <= ARRIVE_RADIUS_SQ && player.onGround()) {
            return MoveOutcome.Done
        }

        steerTowards(target, false)

        if (player.onGround() && player.y < to.feetY - 0.05) {
            Controller.input().jump = true
        }
        return MoveOutcome.Running
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int = 60
}

object FallExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.FALL)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        clearPlannedBreaks(to)?.let { return it }

        val target = nodeCenter(to)
        val landed = (player.onGround() || player.isInWater) && abs(player.y - to.feetY) <= 0.45

        if (landed && horizontalDistanceSq(target) <= ARRIVE_RADIUS_SQ * 2.5) return MoveOutcome.Done

        steerOverLedge(ctx, from, to, false)
        return MoveOutcome.Running
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int = 70
}

object ClimbExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.CLIMB_UP, MoveKind.CLIMB_DOWN)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        val target = nodeCenter(to)

        if (to.kind == MoveKind.CLIMB_UP) {
            if (player.y >= to.feetY - 0.05) return MoveOutcome.Done
            steerTowards(target, false)
            Controller.input().jump = true
        } else {
            if (player.y <= to.feetY + 0.05) return MoveOutcome.Done
            steerTowards(target, false)
            Controller.input().sneak = false
        }
        return MoveOutcome.Running
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int = 60
}

object PillarExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.PILLAR)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        val placeCell = BlockPos.of(to.place)
        val support = ctx.view.profile(placeCell.x, placeCell.y, placeCell.z)

        ctx.view.invalidate(placeCell)

        if (support.fullCube && player.y >= to.feetY - 0.05 && player.onGround()) return MoveOutcome.Done

        val center = Vec3(to.x + 0.5, player.y, to.z + 0.5)
        if (horizontalDistanceSq(center) > ALIGN_RADIUS_SQ) {
            steerTowards(center, false)
            return MoveOutcome.Running
        }
        Controller.stopMoving()

        if (player.onGround()) {
            Controller.input().jump = true
            return MoveOutcome.Running
        }

        if (player.y > placeCell.y + 0.35 && player.deltaMovement.y > -0.2) {
            when (BlockPlacer.place(placeCell, ctx.plan.placeable)) {
                PlaceResult.PLACED, PlaceResult.NEEDS_AIM -> Unit
                PlaceResult.NO_ITEM -> return MoveOutcome.Failed("no placeable block for pillar")
                PlaceResult.NO_SUPPORT -> return MoveOutcome.Failed("no support face for pillar at $placeCell")
            }
        }
        return MoveOutcome.Running
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int = 70
}

object DescendExecutor : MoveExecutor {

    override val kinds = setOf(MoveKind.DESCEND)

    override fun tick(ctx: ExecutionContext, from: PathNode, to: PathNode): MoveOutcome {
        val center = Vec3(from.x + 0.5, player.y, from.z + 0.5)
        if (horizontalDistanceSq(center) > ALIGN_RADIUS_SQ && player.y > to.feetY + 0.5) {
            steerTowards(center, false)
            return MoveOutcome.Running
        }
        Controller.stopMoving()

        clearPlannedBreaks(to)?.let { return it }

        if (abs(player.y - to.feetY) <= 0.35 && player.onGround()) return MoveOutcome.Done
        return MoveOutcome.Running
    }

    override fun timeoutTicks(from: PathNode, to: PathNode): Int = 200
}

object MoveExecutors {

    private val byKind = HashMap<MoveKind, MoveExecutor>()

    init {
        register(WalkExecutor)
        register(JumpExecutor)
        register(FallExecutor)
        register(ClimbExecutor)
        register(PillarExecutor)
        register(DescendExecutor)
    }

    private fun register(executor: MoveExecutor) {
        for (kind in executor.kinds) byKind[kind] = executor
    }

    fun forKind(kind: MoveKind): MoveExecutor = byKind[kind] ?: WalkExecutor
}
