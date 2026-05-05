package org.kvxd.kiwi.agent.job

data class GoalFrame(
    val itemId: String,
    val amount: Int,
    val targetCount: Int,
    val reason: String
) {
    fun remaining(currentCount: Int): Int = (targetCount - currentCount).coerceAtLeast(0)
}
