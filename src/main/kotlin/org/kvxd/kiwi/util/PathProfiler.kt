package org.kvxd.kiwi.util

import net.minecraft.util.Formatting
import org.kvxd.kiwi.client
import org.kvxd.kiwi.pathing.calc.PathResult
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PathProfiler {

    private val logFile = File(client.runDirectory, "kiwi_path_log.csv")

    init {
        if (!logFile.exists()) {
            logFile.writeText("Timestamp,Time(ms),NodesVisited,PathLength,Iter,Success,NPS\n")
        }
    }

    fun record(result: PathResult, success: Boolean) {
        val nps = if (result.timeComputedMs > 0)
            (result.nodesVisited / (result.timeComputedMs / 1000.0)).toLong()
        else 0

        val color = if (success) Formatting.GREEN else Formatting.RED

        ClientMessenger.send {
            element("Status", if (success) "OK" else "FAIL", valueColor = color)
            separator()
            element("Time", String.format("%.2fms", result.timeComputedMs))
            separator()
            element("Vis", result.nodesVisited)
            separator()
            element("NPS", nps)
            separator()
            element("Len", result.path?.size ?: 0)
        }

        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val line =
            "$now,${result.timeComputedMs},${result.nodesVisited},${result.path?.size ?: 0},${result.iterations},$success,$nps\n"

        try {
            logFile.appendText(line)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}