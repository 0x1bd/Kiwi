package org.kvxd.kiwi.bot

import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.config.ConfigData
import java.util.ArrayDeque

object BotLog {

    private const val CAPACITY = 4000

    private val entries = ArrayDeque<String>(CAPACITY)
    private var startedAtMs = System.currentTimeMillis()

    @Synchronized
    fun reset() {
        entries.clear()
        startedAtMs = System.currentTimeMillis()
    }

    @Synchronized
    fun history(): List<String> = entries.toList()

    @Synchronized
    fun append(level: Char, message: String) {
        val elapsed = (System.currentTimeMillis() - startedAtMs) / 1000.0
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast("%8.2fs %s %s".format(elapsed, level, message))
    }

    inline fun debug(message: () -> String) {
        val text = message()
        append('D', text)
        if (ConfigData.debugMode) Kiwi.logger.info("[kiwi] {}", text)
    }

    fun info(message: String) {
        append('I', message)
        Kiwi.logger.info("[kiwi] {}", message)
    }

    fun warn(message: String) {
        append('W', message)
        Kiwi.logger.warn("[kiwi] {}", message)
    }
}
