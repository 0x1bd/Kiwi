package org.kvxd.kiwi

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.planning.PlanningEngine
import org.kvxd.kiwi.agent.ui.AgentOverlayRenderer
import org.kvxd.kiwi.command.CommandRegistry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.recipe.RecipeDatabase
import org.kvxd.kiwi.render.PathRenderer
import org.slf4j.LoggerFactory

class Kiwi : ClientModInitializer {

    companion object {

        const val MOD_ID = "kiwi"

        val logger = LoggerFactory.getLogger(MOD_ID)
    }

    private var harvestLoadTicks = 0

    override fun onInitializeClient() {
        ConfigManager.load()

        logger.info("Kiwi initializing...")

        RecipeDatabase.load()
        RecipeDatabase.dumpStats()
        PlanningEngine.initialize()

        CommandRegistry.init()

        AgentOverlayRenderer.init()
        PathRenderer.init()

        ClientRecipeSynchronizedEvent.EVENT.register { _, _ ->
            RecipeLookup.reloadRecipes()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ConfigManager.save()
        }

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            RotationManager.tick()

            if (!HarvestDatabase.isLoaded && client.level != null) {
                harvestLoadTicks++
                if (harvestLoadTicks >= 10) {
                    try {
                        HarvestDatabase.load()
                        RecipeLookup.reloadRecipes()
                    } catch (e: Exception) {
                        logger.error("Failed to load HarvestDatabase: ${e.message}", e)
                    }
                }
            }
        }
    }
}