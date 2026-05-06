package org.kvxd.kiwi.command.impl

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.client
import org.kvxd.kiwi.command.AbstractCommand
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.feedback
import org.kvxd.kiwi.util.error
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DebugCommand : AbstractCommand("debug") {

    override fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = literal(name)

        root.then(literal("invalidateCache").executes {
            CollisionCache.clearCache()
            it.source.feedback("Caches have been invalidated.")
            1
        })

        root.then(literal("dump").executes {
            dumpState(it.source)
            1
        })

        root.then(literal("toggle").executes {
            ConfigData.debugMode = !ConfigData.debugMode
            it.source.feedback("Debug mode: ${if (ConfigData.debugMode) "ON" else "OFF"}")
            1
        })

        return root
    }

    private fun dumpState(source: FabricClientCommandSource) {
        val dir = File(client.gameDirectory, "config/kiwi")
        dir.mkdirs()

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir, "debug_dump_$timestamp.json")

        val playerPos = player.blockPosition()
        val json = buildString {
            appendLine("{")
            appendLine("  \"timestamp\": \"${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\",")
            appendLine("  \"player\": {")
            appendLine("    \"pos\": [${playerPos.x}, ${playerPos.y}, ${playerPos.z}],")
            appendLine("    \"health\": ${player.health},")
            appendLine("    \"food\": ${player.foodData.foodLevel},")
            appendLine("    \"dimension\": \"${player.level().dimension().identifier()}\"")
            appendLine("  },")
            appendLine("  \"agent\": {")
            appendLine("    \"active\": ${Agent.active},")
            appendLine("    \"phase\": \"${DebugState.agentPhase}\",")
            appendLine("    \"objective\": \"${DebugState.agentObjective}\",")
            appendLine("    \"objectiveAmount\": ${DebugState.agentObjectiveAmount},")
            appendLine("    \"goalCount\": ${DebugState.agentGoalCount},")
            appendLine("    \"goalTop\": \"${DebugState.agentGoalTop}\",")
            appendLine("    \"mineTarget\": ${DebugState.agentMineTarget?.let { "[${it.x},${it.y},${it.z}]" } ?: "null"},")
            appendLine("    \"mineBlockId\": \"${DebugState.agentMineBlockId}\",")
            appendLine("    \"planFailures\": ${DebugState.agentPlanFailures},")
            appendLine("    \"knownBlocks\": ${DebugState.agentKnownBlocks},")
            appendLine("    \"stuckTicks\": ${DebugState.agentStuckTicks}")
            appendLine("  },")
            appendLine("  \"path\": {")
            appendLine("    \"active\": ${DebugState.pathActive},")
            appendLine("    \"calculating\": ${DebugState.pathCalculating},")
            appendLine("    \"size\": ${DebugState.pathSize},")
            appendLine("    \"remaining\": ${DebugState.pathRemaining},")
            appendLine("    \"partial\": ${DebugState.pathPartial},")
            appendLine("    \"goalType\": \"${DebugState.pathGoalType}\",")
            appendLine("    \"lastAction\": \"${DebugState.pathLastAction}\",")
            appendLine("    \"stuckTicks\": ${DebugState.pathStuckTicks},")
            appendLine("    \"goalReached\": ${DebugState.pathGoalReached}")
            appendLine("  },")
            appendLine("  \"config\": {")
            appendLine("    \"debugMode\": ${ConfigData.debugMode},")
            appendLine("    \"blockReach\": ${player.blockInteractionRange()},")
            appendLine("    \"entityReach\": ${player.entityInteractionRange()},")
            appendLine("    \"blockScanRadius\": ${ConfigData.blockScanRadius},")
            appendLine("    \"stuckThresholdTicks\": ${ConfigData.stuckThresholdTicks}")
            appendLine("  },")
            appendLine("  \"recentLog\": [")
            if (DebugState.recentMessages.isEmpty()) {
                appendLine("  ]")
            } else {
                for ((i, msg) in DebugState.recentMessages.withIndex()) {
                    val comma = if (i < DebugState.recentMessages.lastIndex) "," else ""
                    appendLine("    \"${msg.replace("\"", "\\\"")}\"$comma")
                }
                appendLine("  ]")
            }
            appendLine("}")
        }

        try {
            file.writeText(json)
            source.feedback("Debug dump saved to ${file.name}")
            DebugState.log("Debug dump exported: ${file.name}")
        } catch (e: Exception) {
            source.error("Failed to write debug dump: ${e.message}")
        }
    }
}
