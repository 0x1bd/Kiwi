package org.kvxd.kiwi.command

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import org.kvxd.kiwi.command.impl.ConfigCommand
import org.kvxd.kiwi.command.impl.CraftCommand
import org.kvxd.kiwi.command.impl.DebugCommand
import org.kvxd.kiwi.command.impl.GoalCommand
import org.kvxd.kiwi.command.impl.StopCommand
import org.kvxd.kiwi.command.impl.TestCommand

object CommandRegistry {

    private val commands = listOf(
        GoalCommand,
        DebugCommand,
        StopCommand,
        ConfigCommand,
        CraftCommand,
        TestCommand
    )

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = literal("kiwi")

            for (cmd in commands) {
                root.then(cmd.build())
            }

            dispatcher.register(root)
        }
    }
}