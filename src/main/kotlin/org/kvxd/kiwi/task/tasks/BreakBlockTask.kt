package org.kvxd.kiwi.task.tasks

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.control.BreakProgress
import org.kvxd.kiwi.bot.BotLog
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.GoalAdjacent
import org.kvxd.kiwi.player
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class BreakBlockTask(
    val target: BlockPos,
    private val allowClearingObstructions: Boolean = true
) : AbstractTask("break") {

    private var approachTicks = 0
    private var breakTicks = 0
    private var navigating = false
    private var navigationFailed: String? = null
    private var obstructionAttempts = 0

    override fun onStart(ctx: TaskContext) {
        approachTicks = 0
        breakTicks = 0
        navigating = false
        navigationFailed = null
        obstructionAttempts = 0
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (level.getBlockState(target).isAir) {
            ctx.memory.markMined(target)
            return TaskStatus.Success
        }
        navigationFailed?.let { return TaskStatus.Failure(it) }

        if (breakTicks > MAX_BREAK_TICKS) {
            ctx.memory.markFailed(target)
            return TaskStatus.Failure("gave up breaking $target after $MAX_BREAK_TICKS ticks")
        }

        Controller.stopMoving()

        return when (BlockBreaker.tick(target)) {
            BreakProgress.DONE -> {
                ctx.memory.markMined(target)
                TaskStatus.Success
            }

            BreakProgress.BREAKING -> {
                breakTicks++
                approachTicks = 0
                TaskStatus.Running
            }

            BreakProgress.IMPOSSIBLE -> {
                ctx.memory.markFailed(target)
                TaskStatus.Failure("$target cannot be broken")
            }

            BreakProgress.APPROACHING -> handleApproach(ctx)
        }
    }

    private fun handleApproach(ctx: TaskContext): TaskStatus {
        approachTicks++
        if (approachTicks < SETTLE_TICKS) return TaskStatus.Running

        if (BlockBreaker.isInReach(target) && allowClearingObstructions) {
            val obstruction = findObstruction()
            if (obstruction != null && obstructionAttempts < MAX_OBSTRUCTIONS) {
                obstructionAttempts++
                approachTicks = 0
                return TaskStatus.Delegate(BreakBlockTask(obstruction, allowClearingObstructions = false))
            }
        }

        if (navigating) {
            ctx.memory.markFailed(target)
            return TaskStatus.Failure("could not get a clear line to $target")
        }

        navigating = true
        approachTicks = 0
        BotLog.debug { "cannot see $target from here, walking closer" }
        val reach = (player.blockInteractionRange() - 0.75).coerceAtLeast(1.5)
        return TaskStatus.Delegate(NavigateTask(GoalAdjacent(target, reach)))
    }

    private fun findObstruction(): BlockPos? {
        val hit = level.clip(
            net.minecraft.world.level.ClipContext(
                player.eyePosition,
                net.minecraft.world.phys.Vec3.atCenterOf(target),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
            )
        )
        if (hit.type != net.minecraft.world.phys.HitResult.Type.BLOCK) return null
        if (hit.blockPos == target) return null
        val state = level.getBlockState(hit.blockPos)
        if (state.isAir || state.getDestroySpeed(level, hit.blockPos) < 0f) return null
        return hit.blockPos
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (status is TaskStatus.Failure && child is NavigateTask) {
            navigationFailed = status.reason
            ctx.memory.markFailed(target)
        }
    }

    override fun onStop(ctx: TaskContext, status: TaskStatus) {
        BlockBreaker.stop()
    }

    override fun describe(): String = "break [${target.x},${target.y},${target.z}]"

    companion object {
        private const val SETTLE_TICKS = 6
        private const val MAX_BREAK_TICKS = 20 * 30
        private const val MAX_OBSTRUCTIONS = 6
    }
}
