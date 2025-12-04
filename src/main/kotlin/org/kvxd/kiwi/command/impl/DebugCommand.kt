package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.util.feedback

object DebugCommand : AbstractCommand("debug") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = literal(name)

        root.then(literal("invalidateCache").executes {
            CollisionCache.clearCache()

            it.source.feedback("Caches have been invalidated.")
            1
        })

        return root
    }
}