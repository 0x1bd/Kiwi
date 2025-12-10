package org.kvxd.kiwi.command.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.arguments.coordinates.WorldCoordinate
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

class ClientPositionArgument private constructor() : ArgumentType<ClientPositionArgument.Position> {

    data class Position(
        val x: WorldCoordinate,
        val y: WorldCoordinate,
        val z: WorldCoordinate
    ) {

        fun toBlockPos(source: FabricClientCommandSource): BlockPos {
            val pos = source.position

            val absX = x.get(pos.x).toInt()
            val absY = y.get(pos.y).toInt()
            val absZ = z.get(pos.z).toInt()

            return BlockPos(absX, absY, absZ)
        }
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): Position {
        val x = WorldCoordinate.parseInt(reader)
        reader.expect(' ')

        val y = WorldCoordinate.parseInt(reader)
        reader.expect(' ')

        val z = WorldCoordinate.parseInt(reader)

        return Position(x, y, z)
    }

    companion object {

        fun blockPos(): ClientPositionArgument = ClientPositionArgument()

        private val INVALID = DynamicCommandExceptionType {
            Component.literal("Invalid client position: $it")
        }

        fun get(
            ctx: CommandContext<FabricClientCommandSource>,
            name: String
        ): BlockPos {
            val pos = try {
                ctx.getArgument(name, Position::class.java)
            } catch (e: Exception) {
                throw INVALID.create(e.message ?: "Unknown error")
            }

            return pos.toBlockPos(ctx.source)
        }
    }
}
