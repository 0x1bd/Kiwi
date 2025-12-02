package org.kvxd.baobab.command.impl

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.baobab.command.AbstractCommand
import org.kvxd.baobab.config.ConfigData
import org.kvxd.baobab.config.ConfigManager
import org.kvxd.baobab.util.error
import org.kvxd.baobab.util.feedback
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

object ConfigCommand : AbstractCommand("config") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = literal(name)

        root.then(literal("save").executes {
            ConfigManager.save()
            it.source.feedback("Configuration saved to disk.")
            1
        })

        root.then(literal("reload").executes {
            ConfigManager.load()
            it.source.feedback("Configuration reloaded from disk.")
            1
        })

        root.then(literal("list").executes {
            ConfigData::class.memberProperties.forEach { prop ->
                val value = prop.getter.call(ConfigManager.data)
                it.source.feedback("${prop.name}: §f$value")
            }
            1
        })

        val setNode = literal("set")
        val getNode = literal("get")

        val properties = ConfigData::class.memberProperties
            .filterIsInstance<KMutableProperty1<ConfigData, *>>()

        for (prop in properties) {
            val setter = buildSetter(prop)
            if (setter != null) setNode.then(setter)

            getNode.then(literal(prop.name).executes { ctx ->
                val value = prop.get(ConfigManager.data)
                ctx.source.feedback("${prop.name} §7is set to §f$value")
                1
            })
        }

        root.then(setNode)
        root.then(getNode)

        return root
    }

    private fun buildSetter(prop: KMutableProperty1<ConfigData, *>): LiteralArgumentBuilder<FabricClientCommandSource>? {
        val name = prop.name
        val type = prop.returnType.classifier as? KClass<*> ?: return null

        val (argType, getter) = when (type) {
            Int::class -> IntegerArgumentType.integer() to { ctx: CommandContext<FabricClientCommandSource> ->
                IntegerArgumentType.getInteger(
                    ctx,
                    "value"
                )
            }

            Double::class -> DoubleArgumentType.doubleArg() to { ctx: CommandContext<FabricClientCommandSource> ->
                DoubleArgumentType.getDouble(
                    ctx,
                    "value"
                )
            }

            Boolean::class -> BoolArgumentType.bool() to { ctx: CommandContext<FabricClientCommandSource> ->
                BoolArgumentType.getBool(
                    ctx,
                    "value"
                )
            }

            Long::class -> LongArgumentType.longArg() to { ctx: CommandContext<FabricClientCommandSource> ->
                LongArgumentType.getLong(
                    ctx,
                    "value"
                )
            }

            String::class -> StringArgumentType.string() to { ctx: CommandContext<FabricClientCommandSource> ->
                StringArgumentType.getString(
                    ctx,
                    "value"
                )
            }

            else -> return null
        }

        return literal(name)
            .then(
                ClientCommandManager.argument("value", argType)
                    .executes { ctx ->
                        val newValue = getter(ctx)
                        try {
                            @Suppress("UNCHECKED_CAST")
                            (prop as KMutableProperty1<ConfigData, Any>).set(ConfigManager.data, newValue)
                            ConfigManager.save()

                            ctx.source.feedback("Updated §b$name §ato: §f$newValue")
                            1
                        } catch (e: Exception) {
                            ctx.source.error("Error setting value: ${e.message}")
                            0
                        }
                    }
            )
    }
}