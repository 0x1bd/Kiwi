package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.kiwi.KnowledgeBootstrap
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.knowledge.NO_ID
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.error
import org.kvxd.kiwi.util.feedback
import org.kvxd.kiwi.world.BlockProfiles
import org.kvxd.kiwi.world.LevelWorldView
import org.kvxd.kiwi.world.Stances

object DebugCommand : AbstractCommand("debug") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = literal(name)

        root.then(literal("dump").executes { ctx ->
            val file = org.kvxd.kiwi.bot.DebugDump.write()
            ctx.source.feedback("Dump written to ${file.fileName}")
            1
        })

        root.then(literal("log").executes { ctx ->
            val history = org.kvxd.kiwi.bot.BotLog.history()
            if (history.isEmpty()) {
                ctx.source.feedback("No bot activity recorded yet.")
            } else {
                for (line in history.takeLast(LOG_TAIL)) ctx.source.feedback(line)
                ctx.source.feedback("(${history.size} entries, /kiwi debug dump writes them all)")
            }
            1
        })

        root.then(literal("toggle").executes { ctx ->
            ConfigData.debugMode = !ConfigData.debugMode
            ctx.source.feedback("Debug mode: ${if (ConfigData.debugMode) "ON" else "OFF"}")
            1
        })

        root.then(literal("reloadKnowledge").executes { ctx ->
            Knowledge.reset()
            BlockProfiles.clear()
            KnowledgeBootstrap.ensureLoaded()
            ctx.source.feedback(
                "Knowledge reloaded: ${Knowledge.allCraftRecipes.size} craft, ${Knowledge.allSmeltRecipes.size} smelt recipes."
            )
            1
        })

        root.then(literal("cost").then(argument("item", StringArgumentType.word()).executes { ctx ->
            KnowledgeBootstrap.ensureLoaded()
            val name = StringArgumentType.getString(ctx, "item")
            val id = Ids.item(name)
            if (id == NO_ID) {
                ctx.source.error("Unknown item '$name'.")
                return@executes 0
            }
            val cost = Knowledge.acquisitionCost(id)
            ctx.source.feedback(if (cost.isFinite()) "$name costs ~${"%.1f".format(cost)}" else "$name is unobtainable")
            1
        }))

        root.then(literal("stance").executes { ctx ->
            val view = LevelWorldView(org.kvxd.kiwi.level)
            val pos = player.blockPosition()
            val feet = Stances.standingFeetHeight(view, pos.x, pos.y, pos.z)
            val profile = view.profile(pos.x, pos.y - 1, pos.z)
            ctx.source.feedback(
                "stance feet=${if (Stances.isValid(feet)) "%.3f".format(feet) else "none"} " +
                    "support=${profile.shapeKind} top=${"%.3f".format(profile.supportTop)}"
            )
            1
        })

        root.then(literal("profile").then(argument("pos", ClientPositionArgument.blockPos()).executes { ctx ->
            val target = ClientPositionArgument.get(ctx, "pos")
            val view = LevelWorldView(org.kvxd.kiwi.level)
            val profile = view.profile(target)
            ctx.source.feedback(
                "kind=${profile.shapeKind} supportTop=${"%.3f".format(profile.supportTop)} " +
                    "spans=${profile.footprintSpans.joinToString(",") { "%.2f".format(it) }} " +
                    "fluid=${profile.fluid} hazard=${profile.hazard} hardness=${profile.destroySpeed}"
            )
            1
        }))

        root.then(literal("status").executes { ctx ->
            ctx.source.feedback(Bot.status())
            Bot.navigator.lastResult?.let {
                ctx.source.feedback(
                    "last search: ${"%.2f".format(it.durationMs)}ms, ${it.nodesExpanded} expanded, " +
                        "${it.iterations} iterations, status=${it.path.status}"
                )
            }
            1
        })

        return root
    }

    private const val LOG_TAIL = 20
}
