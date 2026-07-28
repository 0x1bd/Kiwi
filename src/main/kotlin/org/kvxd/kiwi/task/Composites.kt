package org.kvxd.kiwi.task

class SequenceTask(
    name: String,
    private val children: List<Task>
) : AbstractTask(name) {

    private var index = 0
    private var childResult: TaskStatus? = null

    override fun onStart(ctx: TaskContext) {
        index = 0
        childResult = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childResult?.let { result ->
            childResult = null
            if (result is TaskStatus.Failure) return result
            index++
        }
        if (index >= children.size) return TaskStatus.Success
        return TaskStatus.Delegate(children[index])
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        childResult = status
    }

    override fun describe(): String =
        "$name[${index + 1}/${children.size}] ${children.getOrNull(index)?.describe() ?: ""}".trim()
}

class SelectorTask(
    name: String,
    private val children: List<Task>
) : AbstractTask(name) {

    private var index = 0
    private var childResult: TaskStatus? = null
    private var lastFailure: String = "no alternatives"

    override fun onStart(ctx: TaskContext) {
        index = 0
        childResult = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childResult?.let { result ->
            childResult = null
            if (result is TaskStatus.Success) return TaskStatus.Success
            if (result is TaskStatus.Failure) lastFailure = result.reason
            index++
        }
        if (index >= children.size) return TaskStatus.Failure(lastFailure)
        return TaskStatus.Delegate(children[index])
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        childResult = status
    }
}

class RetryTask(
    private val factory: () -> Task,
    private val attempts: Int,
    name: String = "retry"
) : AbstractTask(name) {

    private var used = 0
    private var childResult: TaskStatus? = null
    private var lastFailure = "never ran"

    override fun onStart(ctx: TaskContext) {
        used = 0
        childResult = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childResult?.let { result ->
            childResult = null
            if (result is TaskStatus.Success) return TaskStatus.Success
            if (result is TaskStatus.Failure) lastFailure = result.reason
        }
        if (used >= attempts) return TaskStatus.Failure("$lastFailure (after $attempts attempts)")
        used++
        return TaskStatus.Delegate(factory())
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        childResult = status
    }

    override fun describe(): String = "$name[$used/$attempts]"
}

class WaitTask(private val ticks: Int) : AbstractTask("wait") {

    private var remaining = 0

    override fun onStart(ctx: TaskContext) {
        remaining = ticks
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        if (remaining-- <= 0) return TaskStatus.Success
        return TaskStatus.Running
    }
}

class UntilTask(
    name: String,
    private val condition: (TaskContext) -> Boolean,
    private val body: () -> Task,
    private val maxRounds: Int = 64
) : AbstractTask(name) {

    private var rounds = 0
    private var childResult: TaskStatus? = null

    override fun onStart(ctx: TaskContext) {
        rounds = 0
        childResult = null
    }

    override fun tick(ctx: TaskContext): TaskStatus {
        childResult?.let { result ->
            childResult = null
            if (result is TaskStatus.Failure) return result
        }
        if (condition(ctx)) return TaskStatus.Success
        if (rounds++ >= maxRounds) return TaskStatus.Failure("$name did not converge after $maxRounds rounds")
        return TaskStatus.Delegate(body())
    }

    override fun onChildFinished(ctx: TaskContext, child: Task, status: TaskStatus) {
        childResult = status
    }

    override fun describe(): String = "$name[round $rounds]"
}
