package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.command.argument.XZPositionArgument
import org.kvxd.kiwi.path.GoalBlock
import org.kvxd.kiwi.path.GoalNear
import org.kvxd.kiwi.path.GoalXZ
import org.kvxd.kiwi.player
import org.kvxd.kiwi.task.tasks.NavigateTask
import org.kvxd.kiwi.util.feedback

object GoalCommand : AbstractCommand("goal") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                literal("xyz").then(
                    argument("pos", ClientPositionArgument.blockPos()).executes { ctx ->
                        val pos = ClientPositionArgument.get(ctx, "pos")
                        Bot.start(NavigateTask(GoalBlock(pos)))
                        ctx.source.feedback("Heading to ${pos.x}, ${pos.y}, ${pos.z}.")
                        1
                    }
                )
            )
            .then(
                literal("near").then(
                    argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")
                            Bot.start(NavigateTask(GoalNear(pos, 3.0)))
                            ctx.source.feedback("Heading near ${pos.x}, ${pos.y}, ${pos.z}.")
                            1
                        }
                        .then(
                            argument("range", IntegerArgumentType.integer(1)).executes { ctx ->
                                val pos = ClientPositionArgument.get(ctx, "pos")
                                val range = IntegerArgumentType.getInteger(ctx, "range")
                                Bot.start(NavigateTask(GoalNear(pos, range.toDouble())))
                                ctx.source.feedback("Heading within $range of ${pos.x}, ${pos.y}, ${pos.z}.")
                                1
                            }
                        )
                )
            )
            .then(
                literal("xz").then(
                    argument("pos", XZPositionArgument.xz()).executes { ctx ->
                        val pos = XZPositionArgument.get(ctx, "pos")
                        Bot.start(NavigateTask(GoalXZ(pos.x, pos.z, player.blockY)))
                        ctx.source.feedback("Heading to x=${pos.x}, z=${pos.z}.")
                        1
                    }
                )
            )
    }
}
