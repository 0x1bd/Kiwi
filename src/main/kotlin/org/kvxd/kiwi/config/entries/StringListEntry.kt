package org.kvxd.kiwi.config.entries

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import org.kvxd.kiwi.config.ConfigEntry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.config.ConfigRegistry
import org.kvxd.kiwi.util.feedback

class StringListEntry(
    key: String,
    description: String,
    default: List<String>
) : ConfigEntry<List<String>>(key, description, default) {

    override val displayValue: String
        get() = value.joinToString(", ")

    override val defaultDisplayValue: String
        get() = default.joinToString(", ")

    override val defaultCommandValue: String
        get() = default.joinToString(" ")

    override fun serialize(): JsonElement =
        JsonArray(value.map { JsonPrimitive(it) })

    override fun deserialize(json: JsonElement) {
        value = when (json) {
            is JsonArray -> json.jsonArray.map { it.jsonPrimitive.content }
            is JsonPrimitive -> parseValues(json.jsonPrimitive.content)
            else -> emptyList()
        }.normalized()
    }

    override fun buildGetNode() =
        literal(key).executes { ctx ->
            ctx.source.feedback("$key is §f${displayValue.ifEmpty { "(empty)" }}")
            1
        }

    override fun buildSetNode() =
        literal(key).then(
            argument("values", StringArgumentType.greedyString())
                .executes { ctx ->
                    value = parseValues(StringArgumentType.getString(ctx, "values")).normalized()
                    ConfigManager.save()
                    ctx.source.feedback("Updated $key to §f${displayValue.ifEmpty { "(empty)" }}")
                    1
                }
        )

    override fun buildAddNode() =
        literal(key).then(
            argument("values", StringArgumentType.greedyString())
                .executes { ctx ->
                    val additions = parseValues(StringArgumentType.getString(ctx, "values"))
                    value = (value + additions).normalized()
                    ConfigManager.save()
                    ctx.source.feedback("Added to $key: §f${additions.joinToString(", ")}")
                    1
                }
        )

    override fun buildRemoveNode() =
        literal(key).then(
            argument("values", StringArgumentType.greedyString())
                .executes { ctx ->
                    val removals = parseValues(StringArgumentType.getString(ctx, "values")).toSet()
                    value = value.filterNot { it in removals }
                    ConfigManager.save()
                    ctx.source.feedback("Removed from $key: §f${removals.joinToString(", ")}")
                    1
                }
        )

    override fun buildClearNode() =
        literal(key).executes { ctx ->
            value = emptyList()
            ConfigManager.save()
            ctx.source.feedback("Cleared $key")
            1
        }

    private fun parseValues(raw: String): List<String> =
        raw.split(',', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun List<String>.normalized(): List<String> =
        distinct()
}

fun stringList(key: String, description: String, default: List<String>): ConfigEntry<List<String>> {
    val e = StringListEntry(key, description, default)
    ConfigRegistry.register(e)
    return e
}
