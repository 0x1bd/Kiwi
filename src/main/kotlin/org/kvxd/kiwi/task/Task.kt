package org.kvxd.kiwi.task

sealed interface TaskStatus {

    data object Running : TaskStatus

    data object Success : TaskStatus

    data class Failure(val reason: String) : TaskStatus

    data class Delegate(val task: Task) : TaskStatus
}

interface Task {

    val name: String

    fun onStart(ctx: TaskContext) {}

    fun tick(ctx: TaskContext): TaskStatus

    fun onStop(ctx: TaskContext, status: TaskStatus) {}

    fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {}

    fun describe(): String = name
}

abstract class AbstractTask(override val name: String) : Task {
    override fun toString(): String = describe()
}
