package org.kvxd.kiwi.command.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.command.argument.CoordinateArgument
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class ClientPositionArgument private constructor() : ArgumentType<ClientPositionArgument.Position> {

    data class Position(
        val x: CoordinateArgument,
        val y: CoordinateArgument,
        val z: CoordinateArgument
    ) {

        fun toBlockPos(source: FabricClientCommandSource): BlockPos {
            val pos = source.position

            val absX = x.toAbsoluteCoordinate(pos.x).toInt()
            val absY = y.toAbsoluteCoordinate(pos.y).toInt()
            val absZ = z.toAbsoluteCoordinate(pos.z).toInt()

            return BlockPos(absX, absY, absZ)
        }
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): Position {
        val x = CoordinateArgument.parse(reader)
        reader.expect(' ')

        val y = CoordinateArgument.parse(reader)
        reader.expect(' ')

        val z = CoordinateArgument.parse(reader)

        return Position(x, y, z)
    }

    companion object {

        fun blockPos(): ClientPositionArgument = ClientPositionArgument()

        private val INVALID = DynamicCommandExceptionType {
            Text.literal("Invalid client position: $it")
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
