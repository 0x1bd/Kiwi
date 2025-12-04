package org.kvxd.kiwi.util

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.player

private val PREFIX: Text = Text.literal("Kiwi: ").formatted(Formatting.GRAY)

class MessageBuilder {

    val root: MutableText = Text.empty()

    fun text(content: String, color: Formatting = Formatting.WHITE) {
        root.append(Text.literal(content).formatted(color))
    }

    fun element(
        label: String,
        value: Any,
        labelColor: Formatting = Formatting.GRAY,
        valueColor: Formatting = Formatting.WHITE
    ) {
        text("$label: ", labelColor)
        text(value.toString(), valueColor)
    }

    fun separator() {
        text(" | ", Formatting.DARK_GRAY)
    }
}

fun FabricClientCommandSource.feedback(message: String) {
    val text = Text.empty().append(PREFIX).append(Text.literal(message).formatted(Formatting.WHITE))
    sendFeedback(text)
}

fun FabricClientCommandSource.error(message: String) {
    val text = Text.empty().append(PREFIX).append(Text.literal(message).formatted(Formatting.RED))
    sendError(text)
}

object ClientMessenger {

    fun send(block: MessageBuilder.() -> Unit) {
        val builder = MessageBuilder()
        builder.block()

        val finalMessage = Text.empty()
            .append(PREFIX)
            .append(builder.root)

        player.sendMessage(finalMessage, false)
    }

    fun feedback(msg: String) {
        send { text(msg, Formatting.WHITE) }
    }

    fun debug(msg: String) {
        if (ConfigManager.data.debugMode)
            send { text(msg, Formatting.YELLOW) }
    }

    fun error(msg: String) {
        send { text(msg, Formatting.RED) }
    }
}