package org.kvxd.kiwi.config.entries

import com.mojang.brigadier.arguments.BoolArgumentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import org.kvxd.kiwi.config.ConfigEntry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.config.ConfigRegistry
import org.kvxd.kiwi.util.feedback

class BooleanEntry(
    key: String,
    description: String,
    default: Boolean
) : ConfigEntry<Boolean>(key, description, default) {

    override fun serialize(): JsonElement =
        JsonPrimitive(value)

    override fun deserialize(json: JsonElement) {
        value = json.jsonPrimitive.boolean
    }

    override fun buildGetNode() =
        literal(key).executes { ctx ->
            ctx.source.feedback("$key is §f$value")
            1
        }

    override fun buildSetNode() =
        literal(key).then(
            argument("value", BoolArgumentType.bool())
                .executes { ctx ->
                    val new = BoolArgumentType.getBool(ctx, "value")
                    value = new
                    ConfigManager.save()
                    ctx.source.feedback("Updated $key to §f$new")
                    1
                }
        )
}

fun boolean(key: String, description: String, default: Boolean): ConfigEntry<Boolean> {
    val e = BooleanEntry(key, description, default)
    ConfigRegistry.register(e)
    return e
}