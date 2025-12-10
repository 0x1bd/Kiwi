package org.kvxd.kiwi.util

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player

val PREFIX: Component = Component.literal("Kiwi: ").withStyle(ChatFormatting.GREEN)

class MessageBuilder {

    val root: MutableComponent = Component.empty()

    fun text(content: String, color: ChatFormatting = ChatFormatting.WHITE) {
        root.append(Component.literal(content).withStyle(color))
    }

    fun element(
        label: String,
        value: Any,
        labelColor: ChatFormatting = ChatFormatting.GRAY,
        valueColor: ChatFormatting = ChatFormatting.WHITE
    ) {
        text("$label: ", labelColor)
        text(value.toString(), valueColor)
    }

    fun separator() {
        text(" | ", ChatFormatting.DARK_GRAY)
    }
}

fun FabricClientCommandSource.feedback(message: String) {
    val text = Component.empty().append(PREFIX).append(Component.literal(message).withStyle(ChatFormatting.WHITE))
    sendFeedback(text)
}

fun FabricClientCommandSource.error(message: String) {
    val text = Component.empty().append(PREFIX).append(Component.literal(message).withStyle(ChatFormatting.RED))
    sendError(text)
}

object ClientMessenger {

    fun send(block: MessageBuilder.() -> Unit) {
        val builder = MessageBuilder()
        builder.block()

        val finalMessage = Component.empty()
            .append(PREFIX)
            .append(builder.root)

        player.displayClientMessage(finalMessage, false)
    }

    fun feedback(msg: String) {
        send { text(msg, ChatFormatting.WHITE) }
    }

    fun debug(msg: String) {
        if (ConfigData.debugMode)
            send { text(msg, ChatFormatting.YELLOW) }
    }

    fun error(msg: String) {
        send { text(msg, ChatFormatting.RED) }
    }
}