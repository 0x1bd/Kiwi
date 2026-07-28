package org.kvxd.kiwi.task.tasks

import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.bot.BotLog
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.path.GoalPickup
import org.kvxd.kiwi.player
import org.kvxd.kiwi.scan.ItemScan
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class PickupTask(
    private val itemIds: IntArray,
    private val targetCount: Int,
    private val radius: Double = 24.0
) : AbstractTask("pickup") {

    private val skipped = HashSet<Int>()
    private var startingCount = 0
    private var settleTicks = 0
    private var approachTicks = 0
    private var currentEntityId = -1
    private var pathedToCurrent = false

    override fun onStart(ctx: TaskContext) {
        skipped.clear()
        startingCount = ctx.count(itemIds)
        settleTicks = 0
        approachTicks = 0
        currentEntityId = -1
        pathedToCurrent = false
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (ctx.count(itemIds) >= targetCount) return finish(ctx)

        val target = candidates().minByOrNull { it.distanceToSqr(player) }
        if (target == null) {
            return if (settleTicks++ < SETTLE_TICKS) TaskStatus.Running else finish(ctx)
        }
        settleTicks = 0

        if (target.id != currentEntityId) {
            currentEntityId = target.id
            approachTicks = 0
            pathedToCurrent = false
        }

        val position = target.position()
        val itemCell = target.blockPosition()
        val goal = GoalPickup(itemCell)
        val standing = ctx.playerPos()

        if (!goal.isReached(standing.x, standing.y, standing.z)) {
            if (pathedToCurrent) {
                BotLog.debug {
                    "drop ${target.id} at ${itemCell.toShortString()} is out of reach from " +
                        "${standing.toShortString()}"
                }
                return skip(ctx, target)
            }
            pathedToCurrent = true
            return TaskStatus.Delegate(NavigateTask(goal, timeoutTicks = 20 * 20))
        }

        if (approachTicks++ > APPROACH_TICKS) {
            BotLog.debug { "giving up on drop ${target.id} after $APPROACH_TICKS ticks of nudging" }
            return skip(ctx, target)
        }

        val dx = position.x - player.x
        val dz = position.z - player.z

        val direction = Vec3(dx, 0.0, dz)
        if (direction.lengthSqr() < 1.0E-6) {
            Controller.stopMoving()
            return TaskStatus.Running
        }

        LookController.faceHorizontally(player.position(), position)
        Controller.moveTowards(direction.normalize(), sprint = false)

        return TaskStatus.Running
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        if (status is TaskStatus.Failure && currentEntityId != -1) {
            skipped.add(currentEntityId)
            currentEntityId = -1
        }
    }

    private fun skip(ctx: TaskContext, target: ItemEntity): TaskStatus {
        skipped.add(target.id)
        currentEntityId = -1
        approachTicks = 0
        pathedToCurrent = false
        return TaskStatus.Running
    }

    private fun finish(ctx: TaskContext): TaskStatus {
        Controller.stopMoving()
        val gained = ctx.count(itemIds) - startingCount
        if (gained > 0 || skipped.isEmpty()) return TaskStatus.Success
        return TaskStatus.Failure("could not reach any of the ${skipped.size} nearby drops")
    }

    override fun onStop(ctx: TaskContext, status: TaskStatus) {
        Controller.stopMoving()
    }

    private fun candidates(): List<ItemEntity> =
        ItemScan.nearby(itemIds, radius).filter { it.id !in skipped && !it.isRemoved }

    override fun describe(): String = "pickup ($targetCount wanted)"

    companion object {
        private const val SETTLE_TICKS = 15
        private const val APPROACH_TICKS = 50
    }
}
