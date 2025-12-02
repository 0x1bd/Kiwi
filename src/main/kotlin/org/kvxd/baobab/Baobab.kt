package org.kvxd.baobab

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.kvxd.baobab.command.CommandRegistry
import org.kvxd.baobab.config.ConfigManager
import org.kvxd.baobab.control.PathExecutor
import org.kvxd.baobab.world.WorldSnapshot
import org.slf4j.LoggerFactory

class Baobab : ClientModInitializer {

    companion object {

        const val MOD_ID = "baobab"

        val logger = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitializeClient() {
        ConfigManager.load()

        WorldSnapshot.init()

        CommandRegistry.init()

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ConfigManager.save()
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            PathExecutor.tick()
        }
    }
}