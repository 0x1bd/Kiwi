package org.kvxd.baobab.command

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.kvxd.baobab.command.impl.ClearCacheCommand
import org.kvxd.baobab.command.impl.ConfigCommand
import org.kvxd.baobab.command.impl.GotoCommand
import org.kvxd.baobab.command.impl.StopCommand

object CommandRegistry {

    private val commands = listOf(
        ClearCacheCommand,
        GotoCommand,
        StopCommand,
        ConfigCommand
    )

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = ClientCommandManager.literal("baobab")

            for (cmd in commands) {
                root.then(cmd.build())
            }

            dispatcher.register(root)
        }
    }
}