package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.test.TestRunner

object TestCommand : AbstractCommand("test") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name).executes { ctx ->
            TestRunner.runAndReport(ctx.source)
            1
        }
    }
}
