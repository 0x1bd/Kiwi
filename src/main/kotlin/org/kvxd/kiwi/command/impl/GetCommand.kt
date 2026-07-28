package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.KnowledgeBootstrap
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.knowledge.NO_ID
import org.kvxd.kiwi.task.tasks.AcquireItemTask
import org.kvxd.kiwi.util.error
import org.kvxd.kiwi.util.feedback

object GetCommand : AbstractCommand("get") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                argument("item", StringArgumentType.word())
                    .suggests { context, builder ->
                        val prefix = context.input.substringAfterLast(' ').lowercase()
                        for (name in obtainableItems()) {
                            if (name.startsWith(prefix)) builder.suggest(name)
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx -> start(ctx.source, StringArgumentType.getString(ctx, "item"), 1) }
                    .then(
                        argument("count", IntegerArgumentType.integer(1)).executes { ctx ->
                            start(
                                ctx.source,
                                StringArgumentType.getString(ctx, "item"),
                                IntegerArgumentType.getInteger(ctx, "count")
                            )
                        }
                    )
            )
            .then(
                literal("status").executes { ctx ->
                    ctx.source.feedback(Bot.status())
                    Bot.statusLine().takeIf { it.isNotBlank() }?.let { ctx.source.feedback(it) }
                    1
                }
            )
    }

    private fun start(source: FabricClientCommandSource, itemName: String, count: Int): Int {
        KnowledgeBootstrap.ensureLoaded()

        val id = Ids.item(itemName)
        if (id == NO_ID) {
            source.error("Unknown item '$itemName'.")
            return 0
        }
        if (!Knowledge.isObtainable(id)) {
            source.error("Kiwi does not know how to obtain '$itemName'.")
            return 0
        }

        Bot.start(AcquireItemTask(intArrayOf(id), count, itemName))
        source.feedback("Working on $itemName x$count.")
        return 1
    }

    private fun obtainableItems(): List<String> {
        if (!Knowledge.isLoaded) return emptyList()
        val names = LinkedHashSet<String>()
        for (recipe in Knowledge.allCraftRecipes) names.add(Ids.itemName(recipe.result))
        for (recipe in Knowledge.allSmeltRecipes) names.add(Ids.itemName(recipe.result))
        return names.sorted()
    }
}
