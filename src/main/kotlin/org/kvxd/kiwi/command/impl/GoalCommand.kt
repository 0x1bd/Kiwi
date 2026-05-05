package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.command.argument.XZPositionArgument
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalXYZ
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalNear
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalXZ

object GoalCommand : AbstractCommand("goal") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                literal("xyz").then(
                    argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            PathNavigator.setGoal(GoalXYZ(pos))
                            1
                        })
            ).then(
                literal("gotoNear").then(
                    argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            PathNavigator.setGoal(GoalNear(pos, 3.0))
                            1
                        }
                        .then(
                            argument("range", IntegerArgumentType.integer())
                                .executes { ctx ->
                                    val pos = ClientPositionArgument.get(ctx, "pos")
                                    val range = IntegerArgumentType.getInteger(ctx, "range")

                                    PathNavigator.setGoal(GoalNear(pos, range.toDouble()))
                                    1
                                })
                )
            )
            .then(
                literal("xz").then(
                    argument("pos", XZPositionArgument.xz())
                        .executes { ctx ->
                            val pos = XZPositionArgument.get(ctx, "pos")

                            PathNavigator.setGoal(GoalXZ(pos.x, pos.z))
                            1
                        }
                ))
    }
}