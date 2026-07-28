package org.kvxd.kiwi.bot

import net.minecraft.core.registries.BuiltInRegistries
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.knowledge.Knowledge
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DebugDump {

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun write(): Path {
        val directory = client.gameDirectory.toPath().resolve("kiwi").resolve("dumps")
        Files.createDirectories(directory)

        val file = directory.resolve("kiwi-dump-${LocalDateTime.now().format(FILE_STAMP)}.txt")
        Files.writeString(file, render())
        Kiwi.logger.info("Kiwi dump written to {}", file)
        return file
    }

    fun render(): String = buildString {
        appendLine("Kiwi dump ${LocalDateTime.now()}")
        appendLine("=".repeat(72))
        appendSection(this, "bot", botLines())
        appendSection(this, "navigator", navigatorLines())
        appendSection(this, "inventory", inventoryLines())
        appendSection(this, "knowledge", knowledgeLines())
        appendSection(this, "config", configLines())
        appendSection(this, "log", BotLog.history())
    }

    private fun appendSection(builder: StringBuilder, title: String, lines: List<String>) {
        builder.appendLine()
        builder.appendLine("--- $title ${"-".repeat((68 - title.length).coerceAtLeast(0))}")
        if (lines.isEmpty()) builder.appendLine("(empty)")
        for (line in lines) builder.appendLine(line)
    }

    private fun botLines(): List<String> {
        val lines = ArrayList<String>()
        lines.add("busy: ${Bot.isBusy}")
        lines.add("status: ${Bot.status()}")
        lines.add("statusLine: ${Bot.statusLine()}")
        lines.add("lastResult: ${Bot.lastResult}")

        val player = client.player
        if (player != null) {
            lines.add("player: ${"%.2f".format(player.x)}, ${"%.2f".format(player.y)}, ${"%.2f".format(player.z)}")
            lines.add("onGround: ${player.onGround()}  health: ${player.health}  food: ${player.foodData.foodLevel}")
            lines.add("selectedSlot: ${player.inventory.selectedSlot}")
        }

        lines.add("task stack:")
        val trail = Bot.taskTrail()
        if (trail.isEmpty()) lines.add("  (idle)")
        for ((depth, task) in trail.withIndex()) {
            lines.add("  ${"  ".repeat(depth)}${depth + 1}. ${task.describe()}")
        }
        return lines
    }

    private fun navigatorLines(): List<String> {
        val navigator = Bot.navigator
        val lines = ArrayList<String>()
        lines.add("calculating: ${navigator.isCalculating}")

        val path = navigator.path
        lines.add("path: ${navigator.currentIndex}/${path.size} status=${path.status}")
        navigator.lastResult?.let { result ->
            lines.add(
                "last search: status=${result.path.status} failure=${result.failure?.message ?: "none"} " +
                    "expanded=${result.nodesExpanded} iterations=${result.iterations} " +
                    "time=${"%.2f".format(result.durationMs)}ms"
            )
        }
        for (index in navigator.currentIndex until minOf(path.size, navigator.currentIndex + 8)) {
            val node = path.node(index) ?: continue
            val breaks = if (node.breaks.isEmpty()) "" else " breaks=${node.breaks.size}"
            lines.add("  [$index] ${node.kind} -> ${node.x},${node.y},${node.z} feet=${"%.2f".format(node.feetY)}$breaks")
        }
        return lines
    }

    private fun inventoryLines(): List<String> {
        val player = client.player ?: return emptyList()
        val counts = LinkedHashMap<String, Int>()
        for (slot in 0..40) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val name = BuiltInRegistries.ITEM.getKey(stack.item).path
            counts[name] = (counts[name] ?: 0) + stack.count
        }
        if (counts.isEmpty()) return listOf("(empty)")
        return counts.entries.map { "${it.key} x${it.value}" }
    }

    private fun knowledgeLines(): List<String> = listOf(
        "loaded: ${Knowledge.isLoaded}",
        "craft recipes: ${Knowledge.allCraftRecipes.size}",
        "smelt recipes: ${Knowledge.allSmeltRecipes.size}"
    )

    private fun configLines(): List<String> = listOf(
        "debugMode: ${ConfigData.debugMode}",
        "allowBreak: ${ConfigData.allowBreak}",
        "allowPillar: ${ConfigData.allowPillar}",
        "allowWater: ${ConfigData.allowWater}",
        "maxFallHeight: ${ConfigData.maxFallHeight}",
        "blockScanRadius: ${ConfigData.blockScanRadius}",
        "pathSearchMaxIterations: ${ConfigData.pathSearchMaxIterations}"
    )
}
