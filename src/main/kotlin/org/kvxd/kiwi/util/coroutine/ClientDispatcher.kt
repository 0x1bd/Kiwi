package org.kvxd.kiwi.util.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.kvxd.kiwi.client
import java.util.concurrent.Executor

object ClientDispatcher : CoroutineDispatcher(), Executor {

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        client.execute { block.run() }
    }

    override fun execute(command: Runnable) {
        client.execute { command.run() }
    }
}
