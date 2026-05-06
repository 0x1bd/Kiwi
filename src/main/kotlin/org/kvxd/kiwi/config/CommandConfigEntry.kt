package org.kvxd.kiwi.config

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.serialization.json.JsonElement
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

interface CommandConfigEntry<T> {

    fun serialize(): JsonElement
    fun deserialize(json: JsonElement)

    fun buildGetNode(): LiteralArgumentBuilder<FabricClientCommandSource>
    fun buildSetNode(): LiteralArgumentBuilder<FabricClientCommandSource>
    fun buildAddNode(): LiteralArgumentBuilder<FabricClientCommandSource>? = null
    fun buildRemoveNode(): LiteralArgumentBuilder<FabricClientCommandSource>? = null
    fun buildClearNode(): LiteralArgumentBuilder<FabricClientCommandSource>? = null
}
