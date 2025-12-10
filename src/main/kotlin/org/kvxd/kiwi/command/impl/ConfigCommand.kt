package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.text.Text
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.config.ConfigRegistry
import org.kvxd.kiwi.util.PREFIX
import org.kvxd.kiwi.util.feedback

object ConfigCommand : AbstractCommand("config") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = literal(name)

        root.then(literal("save").executes {
            ConfigManager.save()
            it.source.feedback("Config saved.")
            1
        })

        root.then(literal("reload").executes {
            ConfigManager.load()
            it.source.feedback("Config reloaded.")
            1
        })

        root.then(literal("list").executes {
            for ((_, entry) in ConfigRegistry.getEntries()) {
                it.source.sendFeedback(
                    Text.empty().append(PREFIX).append(entry.toDisplayText())
                )
            }
            1
        })

        val setNode = literal("set")
        val getNode = literal("get")

        for ((_, entry) in ConfigRegistry.getEntries()) {
            setNode.then(entry.buildSetNode())
            getNode.then(entry.buildGetNode())
        }

        root.then(setNode)
        root.then(getNode)

        return root
    }
}
