package org.kvxd.kiwi.agent.ui

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.math.RaycastHelper

object AgentOverlayRenderer {

    private val font get() = Minecraft.getInstance().font
    private val scaledRes get() = Minecraft.getInstance().window

    private const val PANEL_X = 8
    private const val PANEL_Y = 8
    private const val PANEL_PADDING = 6
    private const val INDENT = 10
    private const val MAX_PANEL_WIDTH_FRAC = 0.40
    private const val MAX_PANEL_HEIGHT_FRAC = 0.85

    private val lineHeight: Int get() = font.lineHeight + 2

    fun init() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("kiwi", "agent_overlay")
        ) { context, _ ->
            if (!ConfigData.renderAgentOverlay) return@addLast

            val runtime = Agent.runtime
            val lines = if (runtime != null) buildLines(runtime) else buildMovementLines()
            if (lines.isEmpty()) return@addLast

            val screenW = scaledRes.guiScaledWidth
            val screenH = scaledRes.guiScaledHeight
            val maxPanelW = (screenW * MAX_PANEL_WIDTH_FRAC).toInt()
            val maxPanelH = (screenH * MAX_PANEL_HEIGHT_FRAC).toInt()

            val panelW = (lines.maxOf { font.width(it.value) } + PANEL_PADDING * 2 + INDENT * 2).coerceAtMost(maxPanelW)
            val panelH = (lines.size * lineHeight + PANEL_PADDING * 2).coerceAtMost(maxPanelH)
            val panelX = screenW - panelW - PANEL_X

            drawPanel(context, panelX, PANEL_Y, panelW, panelH)
            var y = PANEL_Y + PANEL_PADDING
            for (line in lines) {
                if (y + lineHeight > PANEL_Y + panelH) break
                context.text(font, line.value, panelX + PANEL_PADDING + line.indent, y, line.color.toInt())
                y += lineHeight
            }
        }
    }

    private fun drawPanel(extractor: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        extractor.fill(x, y, x + w, y + h, 0xCC000000.toInt())
        extractor.fill(x, y, x + w, y + 1, 0xFF3A852A.toInt())
        extractor.fill(x, y + h - 1, x + w, y + h, 0xFF3A852A.toInt())
    }

    private data class OverlayLine(val value: String, val color: Long, val indent: Int)

    private fun buildMovementLines(): List<OverlayLine> {
        if (!Agent.active) return emptyList()

        val lines = mutableListOf<OverlayLine>()
        lines.add(OverlayLine("[active] ${DebugState.agentObjective}", 0xFFAAFFAA, 0))
        lines.add(OverlayLine("     Phase: ${DebugState.agentPhase.ifBlank { Agent.phase }}", 0xFFCCCCCC, 0))

        if (DebugState.pathLastAction.isNotBlank()) {
            lines.add(OverlayLine("     Path: ${DebugState.pathLastAction}", 0xFFCCCC88, 0))
        }
        if (DebugState.pathSize > 0) {
            lines.add(OverlayLine("     Nodes: ${DebugState.pathIndex}/${DebugState.pathSize}", 0xFF888888, 0))
        }

        if (ConfigData.debugMode) {
            lines.add(OverlayLine("", 0, 0))
            lines.add(OverlayLine("--- Debug ------------------", 0xFF666666, 0))
            lines.add(OverlayLine("Calculating: ${DebugState.pathCalculating}", 0xFF888888, 0))
            lines.add(OverlayLine("Remaining: ${DebugState.pathRemaining}", 0xFF888888, 0))
            lines.add(OverlayLine("Stuck ticks: ${DebugState.pathStuckTicks}", 0xFF888888, 0))
        }

        return lines
    }

    private fun buildLines(runtime: AgentRuntime): List<OverlayLine> {
        val lines = mutableListOf<OverlayLine>()

        lines.add(OverlayLine("[active] ${runtime.request.label}", 0xFFAAFFAA, 0))
        lines.add(OverlayLine("     Phase: ${runtime.currentPhase}  [${runtime.goals.size} goals]", 0xFFCCCCCC, 0))
        if (runtime.currentPlan.isNotBlank()) {
            val plan = if (runtime.currentPlan.length > 42) runtime.currentPlan.take(40) + ".." else runtime.currentPlan
            lines.add(OverlayLine("     Plan: $plan", 0xFFCCCC88, 0))
        }

        if (runtime.goals.isNotEmpty()) {
            lines.add(OverlayLine("", 0, 0))
            val maxDescChars = 40
            for ((index, goal) in runtime.goals.withIndex()) {
                val branch = if (index == runtime.goals.size - 1) "`-" else "|-"
                val symbol = if (index == runtime.goals.size - 1) ">" else "-"
                val color = if (index == runtime.goals.size - 1) 0xFFFFFF55L else 0xFF888888L
                val desc = "Need ${goal.itemId} x${runtime.remainingFor(goal)}"
                val displayDesc = if (desc.length > maxDescChars) desc.take(maxDescChars - 2) + ".." else desc
                lines.add(OverlayLine("$branch [$symbol] $displayDesc", color, INDENT))
            }
        }

        if (ConfigData.debugMode) {
            lines.add(OverlayLine("", 0, 0))
            lines.add(OverlayLine("--- Debug ------------------", 0xFF666666, 0))

            if (DebugState.agentMineTarget != null) {
                val target = DebugState.agentMineTarget!!
                val dist = player.blockPosition().distSqr(target)
                val distStr = "%.1f".format(kotlin.math.sqrt(dist))
                lines.add(OverlayLine("Mining: ${DebugState.agentMineBlockId} @ $target ($distStr m)", 0xFFAAAAFF, 0))
            }
            val raycast = RaycastHelper.raycast(1.0f)
            val rayText = when (raycast) {
                is BlockHitResult -> "Block ${raycast.blockPos.x},${raycast.blockPos.y},${raycast.blockPos.z}"
                is EntityHitResult -> "Entity ${raycast.entity.displayName.string}"
                else -> "Miss"
            }
            lines.add(OverlayLine("Raycast: $rayText", 0xFF66CCFFL, 0))

            lines.add(OverlayLine("Path: ${DebugState.pathLastAction}", 0xFF888888, 0))
        }

        return lines
    }
}
