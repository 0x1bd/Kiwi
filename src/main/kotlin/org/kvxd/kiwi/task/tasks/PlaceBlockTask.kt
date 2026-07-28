package org.kvxd.kiwi.task.tasks

import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.BlockPlacer
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.PlaceResult
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.GoalNear
import org.kvxd.kiwi.player
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus
import org.kvxd.kiwi.world.Stances

class PlaceBlockTask(private val itemId: Int) : AbstractTask("place") {

    var placedAt: BlockPos? = null
    private set

    private var spot: Spot? = null
    private var ticks = 0
    private var clearAttempts = 0
    private var respots = 0
    private var navigated = false
    private var navigationFailed: String? = null
    private var lastResult: PlaceResult? = null

    override fun onStart(ctx: TaskContext) {
        spot = null
        ticks = 0
        clearAttempts = 0
        respots = 0
        navigated = false
        navigationFailed = null
        placedAt = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        navigationFailed?.let { return TaskStatus.Failure(it) }
        if (ctx.count(itemId) <= 0) return TaskStatus.Failure("no ${Ids.itemName(itemId)} to place")

        val chosen = spot ?: findSpot(ctx)?.also {
            spot = it
            org.kvxd.kiwi.bot.BotLog.debug {
                "place spot ${it.pos.toShortString()} clear=${it.clear?.toShortString() ?: "none"} " +
                    "score=${"%.2f".format(it.score)} state=${level.getBlockState(it.pos)} " +
                    "below=${level.getBlockState(it.pos.below())} " +
                    "player=(${"%.2f".format(player.x)},${"%.2f".format(player.y)},${"%.2f".format(player.z)}) " +
                    "overlaps=${overlapsPlayer(it.pos)}"
            }
        }
        ?: return TaskStatus.Failure("nowhere to place ${Ids.itemName(itemId)}")
        val target = chosen.pos

        if (overlapsPlayer(target)) {
            spot = null
            return TaskStatus.Running
        }

        if (isPlaced(target)) {
            placedAt = target
            ctx.memory.markPlaced(target)
            return TaskStatus.Success
        }

        val obstruction = chosen.clear
        if (obstruction != null && !level.getBlockState(obstruction).isAir) {
            if (clearAttempts++ > MAX_CLEAR_ATTEMPTS) {
                spot = null
                clearAttempts = 0
                return TaskStatus.Running
            }
            return TaskStatus.Delegate(BreakBlockTask(obstruction))
        }

        if (!BlockBreaker.isInReach(target)) {
            if (navigated) return TaskStatus.Failure("cannot get within reach of $target")
            navigated = true
            return TaskStatus.Delegate(NavigateTask(GoalNear(target, 2.0)))
        }

        Controller.stopMoving()

        if (ticks++ > MAX_TICKS) {
            if (respots++ < MAX_RESPOTS) {
                ticks = 0
                spot = null
                return TaskStatus.Running
            }
            return TaskStatus.Failure(
                "placing ${Ids.itemName(itemId)} at $target timed out (last outcome ${lastResult ?: "none"})"
            )
        }

        val outcome = BlockPlacer.place(target) { stack -> stack.item is BlockItem && Ids.itemOf(stack) == itemId }
        lastResult = outcome
        return when (outcome) {
            PlaceResult.PLACED -> {
                if (isPlaced(target)) {
                    placedAt = target
                    ctx.memory.markPlaced(target)
                    TaskStatus.Success
                } else {
                    if (ticks % 20 == 0) {
                        org.kvxd.kiwi.bot.BotLog.debug {
                            "place rejected at ${target.toShortString()}: state=${level.getBlockState(target)} " +
                                "overlaps=${overlapsPlayer(target)} held=${player.mainHandItem}"
                        }
                    }
                    TaskStatus.Running
                }
            }

            PlaceResult.NEEDS_AIM -> TaskStatus.Running
            PlaceResult.NO_ITEM -> TaskStatus.Failure("no ${Ids.itemName(itemId)} in inventory")
            PlaceResult.NO_SUPPORT -> {
                spot = null
                TaskStatus.Running
            }
        }
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (child is NavigateTask && status is TaskStatus.Failure) navigationFailed = status.reason
    }

    private class Spot(val pos: BlockPos, val clear: BlockPos?, val score: Double)

    private fun findSpot(ctx: TaskContext): Spot? {
        val origin = ctx.playerPos()
        var best: Spot? = null

        for (radius in 1..MAX_RADIUS) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != radius) continue
                    for (dy in intArrayOf(0, -1, 1)) {
                        val candidate = evaluate(ctx, BlockPos(origin.x + dx, origin.y + dy, origin.z + dz), origin, dy)
                            ?: continue
                        if (best == null || candidate.score < best.score) best = candidate
                    }
                }
            }
            if (best != null) return best
        }
        return best
    }

    private fun evaluate(ctx: TaskContext, pos: BlockPos, origin: BlockPos, dy: Int): Spot? {
        if (pos == origin || pos == origin.above()) return null
        if (overlapsPlayer(pos)) return null
        if (!hasSupportFace(ctx, pos)) return null

        val profile = ctx.view.profile(pos.x, pos.y, pos.z)
        if (!profile.known) return null

        val clear = when {
            profile.isAir -> null
            isSoftEnoughToClear(ctx, pos, profile) -> pos
            else -> return null
        }

        var score = kotlin.math.sqrt(origin.distSqr(pos))
        if (clear != null) score += CLEAR_PENALTY
        if (!ctx.view.profile(pos.x, pos.y - 1, pos.z).fullCube) score += WEAK_SUPPORT_PENALTY
        if (dy > 0) score += ABOVE_LEVEL_PENALTY
        if (dy < 0) score += BELOW_LEVEL_PENALTY

        return Spot(pos, clear, score)
    }

    private fun isSoftEnoughToClear(
        ctx: TaskContext,
        pos: BlockPos,
        profile: org.kvxd.kiwi.world.BlockProfile
    ): Boolean {
        if (profile.isAir) return false
        if (profile.fullCube) return false
        if (profile.indestructible) return false
        if (profile.destroySpeed > SOFT_HARDNESS) return false
        return pos.asLong() !in ctx.memory.placedCells
    }

    private fun hasSupportFace(ctx: TaskContext, pos: BlockPos): Boolean {
        for (direction in net.minecraft.core.Direction.entries) {
            val neighbour = pos.relative(direction)
            val profile = ctx.view.profile(neighbour.x, neighbour.y, neighbour.z)
            if (!profile.known || !profile.hasCollision) continue
            return true
        }
        return false
    }

    private fun isPlaced(pos: BlockPos): Boolean {
        val expected = (Ids.itemById(itemId) as? BlockItem)?.block ?: return !level.getBlockState(pos).isAir
        return level.getBlockState(pos).`is`(expected)
    }

    private fun overlapsPlayer(pos: BlockPos): Boolean =
        net.minecraft.world.phys.AABB(pos).intersects(player.boundingBox.inflate(PLAYER_CLEARANCE))

    override fun describe(): String = "place ${Ids.itemName(itemId)}"

    companion object {
        private const val MAX_TICKS = 60
        private const val MAX_RESPOTS = 3
        private const val PLAYER_CLEARANCE = 0.05
        private const val MAX_RADIUS = 4
        private const val MAX_CLEAR_ATTEMPTS = 3
        private const val CLEAR_PENALTY = 4.0
        private const val WEAK_SUPPORT_PENALTY = 2.0
        private const val ABOVE_LEVEL_PENALTY = 5.0
        private const val BELOW_LEVEL_PENALTY = 1.5
        private const val SOFT_HARDNESS = 1.0f
    }
}
