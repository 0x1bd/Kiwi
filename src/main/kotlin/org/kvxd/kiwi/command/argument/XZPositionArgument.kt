package org.kvxd.kiwi.command.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

class XZPositionArgument private constructor() : ArgumentType<XZPositionArgument.Position> {

    data class Position(
        val x: Int,
        val z: Int
    )

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): Position {
        val x = reader.readInt()
        reader.expect(' ')
        val z = reader.readInt()

        return Position(x, z)
    }

    companion object {

        fun xz(): XZPositionArgument = XZPositionArgument()

        private val INVALID = DynamicCommandExceptionType {
            Component.literal("Invalid XZ position: $it")
        }

        fun get(
            ctx: CommandContext<FabricClientCommandSource>,
            name: String
        ): Position {
            return try {
                ctx.getArgument(name, Position::class.java)
            } catch (e: Exception) {
                throw INVALID.create(e.message ?: "Unknown error")
            }
        }
    }
}
