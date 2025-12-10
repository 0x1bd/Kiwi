package org.kvxd.kiwi.config.entries

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import org.kvxd.kiwi.config.ConfigEntry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.config.ConfigRegistry
import org.kvxd.kiwi.util.feedback

class StringEntry(
    key: String,
    description: String,
    default: String
) : ConfigEntry<String>(key, description, default) {

    override fun serialize(): JsonElement =
        JsonPrimitive(value)

    override fun deserialize(json: JsonElement) {
        value = json.jsonPrimitive.content
    }

    override fun buildGetNode() =
        literal(key).executes { ctx ->
            ctx.source.feedback("$key is §f\"$value\"")
            1
        }

    override fun buildSetNode() =
        literal(key).then(
            argument("value", StringArgumentType.string())
                .executes { ctx ->
                    val new = StringArgumentType.getString(ctx, "value")
                    value = new
                    ConfigManager.save()
                    ctx.source.feedback("Updated $key")
                    1
                }
        )
}

fun string(key: String, description: String, default: String): ConfigEntry<String> {
    val e = StringEntry(key, description, default)
    ConfigRegistry.register(e)
    return e
}
