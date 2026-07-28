package org.kvxd.kiwi.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.path.PathStatus

object BotOverlayRenderer {

    private const val PANEL_X = 8
    private const val PANEL_Y = 8
    private const val PADDING = 5

    private const val COLOR_TITLE = 0xFFB9F6CA.toInt()
    private const val COLOR_BODY = 0xFFDDDDDD.toInt()
    private const val COLOR_DIM = 0xFF999999.toInt()
    private const val COLOR_FAIL = 0xFFFF8A80.toInt()

    private val font get() = client.font
    private val lineHeight get() = font.lineHeight + 2

    fun init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Kiwi.MOD_ID, "bot_overlay")) { context, _ ->
            if (!ConfigData.renderAgentOverlay) return@addLast
            val lines = buildLines()
            if (lines.isEmpty()) return@addLast
            draw(context, lines)
        }
    }

    private class Line(val text: String, val color: Int)

    private fun buildLines(): List<Line> {
        if (!Bot.isBusy && Bot.navigator.path.isEmpty) return emptyList()

        val lines = ArrayList<Line>(6)
        lines.add(Line("Kiwi  ${Bot.status()}", COLOR_TITLE))

        Bot.statusLine().takeIf { it.isNotBlank() }?.let { lines.add(Line(it, COLOR_BODY)) }

        val path = Bot.navigator.path
        if (!path.isEmpty) {
            val state = if (path.status == PathStatus.PARTIAL) "partial" else "complete"
            lines.add(Line("path ${Bot.navigator.currentIndex}/${path.size} ($state)", COLOR_DIM))
        } else if (Bot.navigator.isCalculating) {
            lines.add(Line("path calculating...", COLOR_DIM))
        }

        if (ConfigData.debugMode) {
            Bot.navigator.lastResult?.let { result ->
                lines.add(
                    Line(
                        "search ${"%.1f".format(result.durationMs)}ms  ${result.nodesExpanded} expanded",
                        COLOR_DIM
                    )
                )
                result.failure?.let { lines.add(Line("last failure: ${it.message}", COLOR_FAIL)) }
            }
        }

        return lines
    }

    private fun draw(context: GuiGraphicsExtractor, lines: List<Line>) {
        val width = lines.maxOf { font.width(it.text) } + PADDING * 2
        val height = lines.size * lineHeight + PADDING * 2

        context.fill(PANEL_X, PANEL_Y, PANEL_X + width, PANEL_Y + height, 0xCC000000.toInt())
        context.fill(PANEL_X, PANEL_Y, PANEL_X + width, PANEL_Y + 1, 0xFF3A852A.toInt())
        context.fill(PANEL_X, PANEL_Y + height - 1, PANEL_X + width, PANEL_Y + height, 0xFF3A852A.toInt())

        var y = PANEL_Y + PADDING
        for (line in lines) {
            context.text(font, line.text, PANEL_X + PADDING, y, line.color)
            y += lineHeight
        }
    }
}
