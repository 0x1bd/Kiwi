package org.kvxd.kiwi.agent.job

class GoalAgenda(
    private val inventoryCount: (String) -> Int,
    private val isBlocked: (String) -> Boolean,
    private val onChanged: () -> Unit
) {
    enum class PushResult {
        PUSHED,
        EXTENDED,
        DUPLICATE,
        BLOCKED,
        INVALID
    }

    private val stack = mutableListOf<GoalFrame>()

    val items: List<GoalFrame>
        get() = stack

    val topLabel: String
        get() = stack.lastOrNull()?.itemId.orEmpty()

    fun seed(request: AgentRequest) {
        stack.clear()
        stack.add(
            GoalFrame(
                itemId = request.itemId,
                amount = request.amount,
                targetCount = request.amount,
                reason = "user request"
            )
        )
        onChanged()
    }

    fun push(itemId: String, amount: Int, reason: String): PushResult {
        if (amount <= 0) return PushResult.INVALID
        if (isBlocked(itemId)) return PushResult.BLOCKED

        val targetCount = inventoryCount(itemId) + amount
        val existingIndex = stack.indexOfFirst { it.itemId == itemId }
        if (existingIndex != -1) {
            val existing = stack[existingIndex]
            if (targetCount > existing.targetCount) {
                stack[existingIndex] = existing.copy(
                    amount = existing.amount + amount,
                    targetCount = targetCount,
                    reason = reason
                )
                onChanged()
                return PushResult.EXTENDED
            }
            return PushResult.DUPLICATE
        }

        stack.add(
            GoalFrame(
                itemId = itemId,
                amount = amount,
                targetCount = targetCount,
                reason = reason
            )
        )
        onChanged()
        return PushResult.PUSHED
    }

    fun pop(): GoalFrame? {
        if (stack.isEmpty()) return null
        val removed = stack.removeAt(stack.lastIndex)
        onChanged()
        return removed
    }

    fun popCompleted() {
        var changed = false
        while (stack.isNotEmpty()) {
            val top = stack.last()
            if (inventoryCount(top.itemId) < top.targetCount) break
            stack.removeAt(stack.lastIndex)
            changed = true
        }
        if (changed) onChanged()
    }

    fun top(): GoalFrame? = stack.lastOrNull()

    fun remainingFor(goal: GoalFrame): Int = goal.remaining(inventoryCount(goal.itemId))

    fun targetCountFor(goal: GoalFrame): Int = goal.targetCount
}
