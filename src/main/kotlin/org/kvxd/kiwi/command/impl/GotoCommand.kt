package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.command.argument.ClientPositionArgument
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.pathing.goal.BlockGoal

object GotoCommand : AbstractCommand("goto") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                ClientCommandManager.argument("pos", ClientPositionArgument.blockPos())
                    .executes { ctx ->
                        val pos = ClientPositionArgument.get(ctx, "pos")

                        PathExecutor.setGoal(BlockGoal(pos))
                        1
                    })
    }
}