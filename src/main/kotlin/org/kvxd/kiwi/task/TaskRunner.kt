package org.kvxd.kiwi.task

import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.BotLog

class TaskRunner {

    private val stack = ArrayList<Task>()
    private var pendingChildResult: Pair<Task, TaskStatus>? = null

    var lastStatus: TaskStatus = TaskStatus.Success
    private set

    val isRunning: Boolean get() = stack.isNotEmpty()

    val depth: Int get() = stack.size

    fun activeTask(): Task? = stack.lastOrNull()

    fun trail(): List<Task> = stack.toList()

    fun start(ctx: TaskContext, task: Task) {
        clear(ctx)
        push(ctx, task)
        lastStatus = TaskStatus.Running
    }

    fun clear(ctx: TaskContext) {
        while (stack.isNotEmpty()) {
            val task = stack.removeAt(stack.lastIndex)
            runCatching { task.onStop(ctx, TaskStatus.Failure("cancelled")) }
        }
        pendingChildResult = null
    }

    fun tick(ctx: TaskContext): TaskStatus {
        if (stack.isEmpty()) return lastStatus

        pendingChildResult?.let { (child, status) ->
            pendingChildResult = null
            stack.lastOrNull()?.onChildFinished(ctx, child, status)
        }

        val task = stack.lastOrNull() ?: return lastStatus

        val status = try {
            task.tick(ctx)
        } catch (e: Throwable) {
            Kiwi.logger.error("Kiwi task '${task.name}' crashed", e)
            TaskStatus.Failure("${task.name} crashed: ${e.message ?: e::class.simpleName}")
        }

        when (status) {
            TaskStatus.Running -> lastStatus = TaskStatus.Running

            is TaskStatus.Delegate -> {
                lastStatus = TaskStatus.Running
                push(ctx, status.task)
            }

            TaskStatus.Success, is TaskStatus.Failure -> {
                pop(ctx, task, status)
                lastStatus = if (stack.isEmpty()) status else TaskStatus.Running
            }
        }

        return lastStatus
    }

    private fun push(ctx: TaskContext, task: Task) {
        BotLog.debug { "task push: ${task.describe()} (depth ${stack.size + 1})" }
        stack.add(task)
        runCatching { task.onStart(ctx) }.onFailure {
            Kiwi.logger.error("Kiwi task '${task.name}' failed to start", it)
        }
    }

    private fun pop(ctx: TaskContext, task: Task, status: TaskStatus) {
        BotLog.debug { "task pop: ${task.name} -> $status" }
        stack.removeAt(stack.lastIndex)
        runCatching { task.onStop(ctx, status) }
        if (stack.isNotEmpty()) pendingChildResult = task to status
    }
}
