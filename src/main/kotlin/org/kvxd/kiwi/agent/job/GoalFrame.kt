package org.kvxd.kiwi.agent.job

data class GoalFrame(
    val itemId: String,
    val amount: Int,
    val targetCount: Int,
    val reason: String,
    val acceptedItemIds: Set<String> = setOf(itemId),
    val displayName: String? = null
) {
    val label: String
        get() = displayName ?: if (acceptedItemIds.size == 1) itemId else acceptedItemIds.joinToString("|")

    fun remaining(currentCount: Int): Int = (targetCount - currentCount).coerceAtLeast(0)

    fun currentCount(inventoryCounts: Map<String, Int>): Int =
        acceptedItemIds.sumOf { inventoryCounts[it] ?: 0 }

    fun remaining(inventoryCounts: Map<String, Int>): Int =
        remaining(currentCount(inventoryCounts))
}
