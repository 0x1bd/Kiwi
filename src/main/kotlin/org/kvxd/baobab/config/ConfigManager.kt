package org.kvxd.baobab.config

import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import org.kvxd.baobab.Baobab
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ConfigManager {

    private val configDir = FabricLoader.getInstance().configDir
    private val configFile = configDir.resolve("baobab.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var data: ConfigData = ConfigData()
        private set

    fun load() {
        if (!configFile.exists()) {
            save()
            return
        }

        try {
            val content = configFile.readText()
            data = json.decodeFromString<ConfigData>(content)
            Baobab.logger.info("Config loaded.")
        } catch (e: Exception) {

            Baobab.logger.warn("Failed to load config, using defaults: ${e.message}")
            e.printStackTrace()
            val backup = configDir.resolve("baobab.json.bak")
            Files.copy(configFile, backup, StandardCopyOption.REPLACE_EXISTING)
            data = ConfigData()
        }
    }

    fun save() {
        try {
            val content = json.encodeToString(data)
            configFile.writeText(content)
        } catch (e: Exception) {
            Baobab.logger.warn("Failed to save config: ${e.message}")
        }
    }

}