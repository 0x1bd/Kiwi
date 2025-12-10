package org.kvxd.kiwi.config.entries

import com.mojang.brigadier.arguments.IntegerArgumentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import org.kvxd.kiwi.config.ConfigEntry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.config.ConfigRegistry
import org.kvxd.kiwi.util.feedback

class IntEntry(
    key: String,
    description: String,
    default: Int
) : ConfigEntry<Int>(key, description, default) {

    override fun serialize(): JsonElement =
        JsonPrimitive(value)

    override fun deserialize(json: JsonElement) {
        value = json.jsonPrimitive.int
    }

    override fun buildGetNode() =
        literal(key).executes { ctx ->
            ctx.source.feedback("$key is §f$value")
            1
        }

    override fun buildSetNode() =
        literal(key).then(
            argument("value", IntegerArgumentType.integer())
                .executes { ctx ->
                    val new = IntegerArgumentType.getInteger(ctx, "value")
                    value = new
                    ConfigManager.save()
                    ctx.source.feedback("Updated $key to §f$new")
                    1
                }
        )
}

fun int(key: String, description: String, default: Int): ConfigEntry<Int> {
    val e = IntEntry(key, description, default)
    ConfigRegistry.register(e)
    return e
}