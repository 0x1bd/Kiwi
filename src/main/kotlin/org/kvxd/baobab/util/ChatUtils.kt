package org.kvxd.baobab.util

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.kvxd.baobab.player

private val BAOBAB_PREFIX: Text =
    Text.literal("Baobab: ").formatted(Formatting.GRAY)

private fun prefixed(message: String, color: Formatting): Text {
    return Text.empty()
        .append(BAOBAB_PREFIX)
        .append(Text.literal(message).formatted(color))
}

fun FabricClientCommandSource.feedback(message: String) {
    sendFeedback(prefixed(message, Formatting.WHITE))
}

fun FabricClientCommandSource.error(message: String) {
    sendError(prefixed(message, Formatting.RED))
}

object ClientMessenger {

    fun feedback(msg: String) {
        player.sendMessage(prefixed(msg, Formatting.WHITE), false)
    }

    fun error(msg: String) {
        player.sendMessage(prefixed(msg, Formatting.RED), false)
    }
}