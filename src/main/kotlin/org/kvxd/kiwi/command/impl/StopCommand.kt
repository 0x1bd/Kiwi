package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.util.feedback

object StopCommand : AbstractCommand("stop") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name).executes { ctx ->
            Bot.stop()
            ctx.source.feedback("Stopped.")
            1
        }
    }
}
