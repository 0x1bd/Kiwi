package org.kvxd.kiwi.agent.job

data class AgentRequest(
    val itemId: String,
    val amount: Int
) {
    init {
        require(itemId.isNotBlank()) { "itemId must not be blank" }
        require(amount > 0) { "amount must be positive" }
    }

    val label: String get() = "$itemId x$amount"

    fun remaining(currentCount: Int): Int = (amount - currentCount).coerceAtLeast(0)
}
