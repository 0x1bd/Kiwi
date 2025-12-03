package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.util.feedback
import org.kvxd.kiwi.world.WorldSnapshot

object ClearCacheCommand : AbstractCommand("clearCache") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .executes { ctx ->
                WorldSnapshot.clear()

                ctx.source.feedback("Cache cleared.")
                1
            }
    }
}