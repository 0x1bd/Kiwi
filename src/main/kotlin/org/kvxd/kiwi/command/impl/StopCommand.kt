package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.util.feedback

object StopCommand : AbstractCommand("stop") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name).executes { ctx ->
            PathExecutor.stop()
            ctx.source.feedback("Pathing stopped.")
            1
        }
    }
}