package org.kvxd.kiwi.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

abstract class AbstractCommand(val name: String) {

    abstract fun build(): LiteralArgumentBuilder<FabricClientCommandSource>
}