package org.kvxd.kiwi.agent.control

class ReplanLimiter(
    private val minIntervalMs: Long = 650L
) {
    private var lastReplanAtMs: Long = 0L
    private var pendingReason: String? = null

    fun request(reason: String) {
        pendingReason = reason
    }

    fun consumeReady(): String? {
        val reason = pendingReason ?: return null
        val now = System.currentTimeMillis()
        if (now - lastReplanAtMs < minIntervalMs) return null

        pendingReason = null
        lastReplanAtMs = now
        return reason
    }

    fun reset() {
        pendingReason = null
        lastReplanAtMs = 0L
    }
}
