package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.command.argument.XZPositionArgument
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalXYZ
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalNear
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalXZ
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.feedback

object GoalCommand : AbstractCommand("goal") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                literal("xyz").then(
                    argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            Agent.startMovementGoal(
                                GoalXYZ(pos),
                                "move to ${pos.x}, ${pos.y}, ${pos.z}"
                            )
                            ctx.source.feedback("Movement goal set to ${pos.x}, ${pos.y}, ${pos.z}.")
                            1
                        })
            ).then(
                literal("gotoNear").then(
                    argument("pos", ClientPositionArgument.blockPos())
                        .executes { ctx ->
                            val pos = ClientPositionArgument.get(ctx, "pos")

                            Agent.startMovementGoal(
                                GoalNear(pos, 3.0),
                                "move near ${pos.x}, ${pos.y}, ${pos.z}"
                            )
                            ctx.source.feedback("Movement goal set near ${pos.x}, ${pos.y}, ${pos.z}.")
                            1
                        }
                        .then(
                            argument("range", IntegerArgumentType.integer())
                                .executes { ctx ->
                                    val pos = ClientPositionArgument.get(ctx, "pos")
                                    val range = IntegerArgumentType.getInteger(ctx, "range")

                                    Agent.startMovementGoal(
                                        GoalNear(pos, range.toDouble()),
                                        "move within $range of ${pos.x}, ${pos.y}, ${pos.z}"
                                    )
                                    ctx.source.feedback("Movement goal set within $range of ${pos.x}, ${pos.y}, ${pos.z}.")
                                    1
                                })
                )
            )
            .then(
                literal("xz").then(
                    argument("pos", XZPositionArgument.xz())
                        .executes { ctx ->
                            val pos = XZPositionArgument.get(ctx, "pos")

                            Agent.startMovementGoal(
                                GoalXZ(pos.x, pos.z, player.blockY),
                                "move to x=${pos.x}, z=${pos.z}"
                            )
                            ctx.source.feedback("Movement goal set to x=${pos.x}, z=${pos.z}.")
                            1
                        }
                ))
    }
}