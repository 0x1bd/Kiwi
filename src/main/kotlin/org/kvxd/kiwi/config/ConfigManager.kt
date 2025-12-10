package org.kvxd.kiwi.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import net.fabricmc.loader.api.FabricLoader
import org.kvxd.kiwi.Kiwi
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ConfigManager {

    private val file = FabricLoader.getInstance().configDir.resolve("${Kiwi.MOD_ID}.json")

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    init {
        @Suppress("UNUSED_EXPRESSION")
        // statically load the config so that we can access all variables later
        ConfigData
    }

    fun load() {

        if (!file.exists()) return save()

        val obj = json.decodeFromString(JsonObject.serializer(), file.readText())

        for ((key, entry) in ConfigRegistry.getEntries()) {
            obj[key]?.let { entry.deserialize(it) }
        }
    }

    fun save() {
        val root = buildJsonObject {
            for ((key, entry) in ConfigRegistry.getEntries()) {
                put(key, entry.serialize())
            }
        }

        file.writeText(json.encodeToString(JsonObject.serializer(), root))
    }
}
