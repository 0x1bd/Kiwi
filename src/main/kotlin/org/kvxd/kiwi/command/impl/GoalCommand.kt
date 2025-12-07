package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.command.argument.XZPositionArgument
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.pathing.goal.goals.GoalBlock
import org.kvxd.kiwi.pathing.goal.goals.GoalNear
import org.kvxd.kiwi.pathing.goal.goals.GoalXZ

object GoalCommand : AbstractCommand("goal") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                literal("goto").then(
                    ClientCommandManager.argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            PathExecutor.setGoal(GoalBlock(pos))
                            1
                        })
            ).then(
                literal("gotoNear").then(
                    ClientCommandManager.argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            PathExecutor.setGoal(GoalNear(pos, 3.0))
                            1
                        }
                        .then(
                            ClientCommandManager.argument("range", IntegerArgumentType.integer())
                                .executes { ctx ->
                                    val pos = ClientPositionArgument.get(ctx, "pos")
                                    val range = IntegerArgumentType.getInteger(ctx, "range")

                                    PathExecutor.setGoal(GoalNear(pos, range.toDouble()))
                                    1
                                })
                )
            )
            .then(
                literal("xz").then(
                    ClientCommandManager.argument("pos", XZPositionArgument.xz())
                        .executes { ctx ->
                            val pos = XZPositionArgument.get(ctx, "pos")

                            PathExecutor.setGoal(GoalXZ(pos.x, pos.z))
                            1
                        }
                ))
    }
}