package org.kvxd.kiwi.task.tasks

import org.kvxd.kiwi.path.PathGoal
import org.kvxd.kiwi.nav.NavStatus
import org.kvxd.kiwi.task.AbstractTask
import org.kvxd.kiwi.task.TaskContext
import org.kvxd.kiwi.task.TaskStatus

class NavigateTask(
    private val goal: PathGoal,
    private val timeoutTicks: Int = 20 * 90
) : AbstractTask("navigate") {

    private var ticks = 0

    override fun onStart(ctx: TaskContext) {
        ticks = 0
        ctx.navigator.start(goal)
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (ticks++ > timeoutTicks) {
            ctx.navigator.cancel()
            return TaskStatus.Failure("navigation to ${goal.describe()} timed out")
        }

        return when (val status = ctx.navigator.tick()) {
            NavStatus.Reached -> TaskStatus.Success
            NavStatus.Idle -> TaskStatus.Success
            NavStatus.Calculating, NavStatus.Following -> TaskStatus.Running
            is NavStatus.Failed -> TaskStatus.Failure("cannot reach ${goal.describe()}: ${status.reason}")
        }
    }

    override fun onStop(ctx: TaskContext, status: TaskStatus) {
        ctx.navigator.cancel()
    }

    override fun describe(): String = "navigate ${goal.describe()}"
}
