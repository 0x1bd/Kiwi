package org.kvxd.kiwi.agent.ui

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DebugState {

    var pathActive = false
    var pathCalculating = false
    var pathSize = 0
    var pathIndex = 0
    var pathRemaining = 0
    var pathPartial = false
    var pathGoalType = ""
    var pathStuckTicks = 0
    var pathLastAction = ""
    var pathLastActionResult = ""
    var pathGoalReached = false

    var agentPhase = ""
    var agentObjective = ""
    var agentObjectiveAmount = 0
    var agentMineTarget: BlockPos? = null
    var agentMineBlockId = ""
    var agentMineRemaining = 0
    var raycastLabel = ""
    var agentStuckTicks = 0
    var agentGoalCount = 0
    var agentGoalTop = ""
    var agentPlanFailures = 0
    var agentKnownBlocks = 0

    var lastMessageTime = 0L
    var recentMessages = mutableListOf<String>()
    var pathTrace = mutableListOf<String>()
    val pathTraceFilePath: String?
        get() = pathTraceFile?.absolutePath

    private var pathTraceFile: File? = null
    private const val MAX_RECENT = 8
    private const val MAX_PATH_TRACE = 250

    fun reset() {
        pathActive = false
        pathCalculating = false
        pathSize = 0
        pathIndex = 0
        pathRemaining = 0
        pathPartial = false
        pathGoalType = ""
        pathStuckTicks = 0
        pathLastAction = ""
        pathLastActionResult = ""
        pathGoalReached = false
        agentPhase = ""
        agentObjective = ""
        agentObjectiveAmount = 0
        agentMineTarget = null
        agentMineBlockId = ""
        agentMineRemaining = 0
        raycastLabel = ""
        agentStuckTicks = 0
        agentGoalCount = 0
        agentGoalTop = ""
        agentPlanFailures = 0
        agentKnownBlocks = 0
        recentMessages.clear()
    }

    fun log(message: String) {
        recentMessages.add(message)
        if (recentMessages.size > MAX_RECENT) {
            recentMessages.removeAt(0)
        }
        lastMessageTime = System.currentTimeMillis()
    }

    fun tracePath(message: String) {
        if (!ConfigData.debugMode) return

        val entry = "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} ${System.currentTimeMillis()} $message"
        pathTrace.add(entry)
        if (pathTrace.size > MAX_PATH_TRACE) {
            pathTrace.removeAt(0)
        }
        appendPathTrace(entry)
        Kiwi.logger.info("[PathTrace] $message")
    }

    fun startPathTraceLog(): File {
        val dir = File(client.gameDirectory, "config/kiwi")
        dir.mkdirs()

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir, "path_trace_$timestamp.log")
        pathTraceFile = file
        pathTrace.clear()
        file.writeText(
            buildString {
                appendLine("# Kiwi path trace")
                appendLine("# started=${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
                appendLine("# epoch_ms=${System.currentTimeMillis()}")
            }
        )
        return file
    }

    fun stopPathTraceLog() {
        tracePath("debug mode disabled")
    }

    private fun appendPathTrace(entry: String) {
        val file = pathTraceFile ?: startPathTraceLog()
        try {
            file.appendText("$entry\n")
        } catch (e: Exception) {
            Kiwi.logger.warn("Failed to write path trace: ${e.message}", e)
        }
    }
}
