package org.kvxd.kiwi.util.coroutine

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.time.Duration

object ClientTickDelay {

    private data class Waiter(
        var remainingTicks: Int,
        val resume: () -> Unit
    )

    private val waiters = mutableListOf<Waiter>()

    fun tick() {
        if (waiters.isEmpty()) return

        val ready = mutableListOf<Waiter>()
        val iterator = waiters.iterator()
        while (iterator.hasNext()) {
            val waiter = iterator.next()
            waiter.remainingTicks--
            if (waiter.remainingTicks <= 0) {
                iterator.remove()
                ready.add(waiter)
            }
        }

        for (waiter in ready) {
            waiter.resume()
        }
    }

    suspend fun waitTicks(ticks: Int) {
        val waitTicks = max(1, ticks)
        suspendCancellableCoroutine { continuation ->
            val waiter = Waiter(waitTicks) {
                if (continuation.isActive) continuation.resume(Unit)
            }
            waiters.add(waiter)
            continuation.invokeOnCancellation {
                waiters.remove(waiter)
            }
        }
    }

    suspend fun wait(duration: Duration) {
        waitTicks(max(1, ((duration.inWholeMilliseconds + 49L) / 50L).toInt()))
    }
}

suspend fun waitClientTicks(ticks: Int) {
    ClientTickDelay.waitTicks(ticks)
}

suspend fun waitClient(duration: Duration) {
    ClientTickDelay.wait(duration)
}
