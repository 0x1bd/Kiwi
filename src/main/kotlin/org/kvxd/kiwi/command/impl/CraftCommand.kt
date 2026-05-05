package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.util.PREFIX
import org.kvxd.kiwi.util.feedback

object CraftCommand : AbstractCommand("craft") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                argument("item", StringArgumentType.word())
                    .suggests { context, builder ->
                        val prefix = context.input.substringAfterLast(' ').lowercase()

                        val allIds = getObtainableItemIds()
                        for (item in allIds) {
                            if (item.startsWith(prefix)) {
                                builder.suggest(item)
                            }
                        }
                        builder.buildFuture()
                    }
                    .then(
                        argument("count", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val item = StringArgumentType.getString(ctx, "item")
                                val count = IntegerArgumentType.getInteger(ctx, "count")
                                Agent.startItemGoal(item, count)
                                ctx.source.feedback("Starting goal $item x$count - ${if (ConfigData.debugMode) "(debug ON)" else "use /kiwi debug toggle for detail"}")
                                1
                            }
                    )
                    .executes { ctx ->
                        val item = StringArgumentType.getString(ctx, "item")
                        Agent.startItemGoal(item, 1)
                        ctx.source.feedback("Starting goal $item - ${if (ConfigData.debugMode) "(debug ON)" else "use /kiwi debug toggle for detail"}")
                        1
                    }
            )
            .then(
                literal("status")
                    .executes { ctx ->
                        val source = ctx.source
                        if (!Agent.active) {
                            source.feedback("No active goal. Use /kiwi craft <item> to start one.")
                            return@executes 1
                        }
                        source.sendFeedback(
                            Component.empty()
                                .append(PREFIX)
                                .append(Component.literal("Status: ").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(Agent.status).withStyle(ChatFormatting.AQUA))
                        )
                        if (ConfigData.debugMode) {
                            source.sendFeedback(
                                Component.empty()
                                    .append(PREFIX)
                                    .append(Component.literal("Phase: ${Agent.phase} | Debug: ON | /kiwi debug dump to export").withStyle(ChatFormatting.GRAY))
                            )
                        }
                        1
                    }
            )
    }

    private fun getObtainableItemIds(): List<String> {
        val ids = linkedSetOf<String>()

        for (recipe in RecipeLookup.recipes) {
            ids.add(recipe.resultId)
        }

        for (harvest in RecipeLookup.harvestByBlock.values) {
            ids.add(harvest.dropId)
        }

        return ids.sorted()
    }
}
