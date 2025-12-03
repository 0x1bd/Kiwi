package org.kvxd.kiwi

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.kvxd.kiwi.command.CommandRegistry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.render.PathRenderer
import org.slf4j.LoggerFactory

class Kiwi : ClientModInitializer {

    companion object {

        const val MOD_ID = "kiwi"

        val logger = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitializeClient() {
        ConfigManager.load()

        CommandRegistry.init()

        PathRenderer.init()

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ConfigManager.save()
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            PathExecutor.tick()
        }
    }
}