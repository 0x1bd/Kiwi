package org.kvxd.kiwi.command

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.kvxd.kiwi.command.impl.ConfigCommand
import org.kvxd.kiwi.command.impl.GotoCommand
import org.kvxd.kiwi.command.impl.StopCommand

object CommandRegistry {

    private val commands = listOf(
        GotoCommand,
        StopCommand,
        ConfigCommand
    )

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = ClientCommandManager.literal("kiwi")

            for (cmd in commands) {
                root.then(cmd.build())
            }

            dispatcher.register(root)
        }
    }
}