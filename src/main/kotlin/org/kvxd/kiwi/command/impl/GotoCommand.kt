package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.control.PathExecutor

object GotoCommand : AbstractCommand("goto") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal(name)
            .then(
                ClientCommandManager.argument("x", IntegerArgumentType.integer())
                    .then(
                        ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            .then(
                                ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes { ctx ->
                                        val x = IntegerArgumentType.getInteger(ctx, "x")
                                        val y = IntegerArgumentType.getInteger(ctx, "y")
                                        val z = IntegerArgumentType.getInteger(ctx, "z")

                                        val client = ctx.source.client
                                        PathExecutor.computeAndSet(BlockPos(x, y, z))
                                        1
                                    }
                            )
                    )
            )
    }
}