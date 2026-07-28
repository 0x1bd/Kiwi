package org.kvxd.kiwi

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.command.CommandRegistry
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.control.LookController
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.render.BotOverlayRenderer
import org.kvxd.kiwi.render.PathRenderer
import org.slf4j.LoggerFactory

class Kiwi : ClientModInitializer {

    companion object {
        const val MOD_ID = "kiwi"
        val logger = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitializeClient() {
        ConfigManager.load()
        HeadlessAudio.apply()
        CommandRegistry.init()
        PathRenderer.init()
        BotOverlayRenderer.init()

        ClientTickEvents.START_CLIENT_TICK.register { client ->
            HeadlessAudio.tick(client)
            KnowledgeBootstrap.tick()
            Bot.tick()
        }

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            LookController.tick()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            Bot.stop()
            ConfigManager.save()
        }

        logger.info("Kiwi initialised")
    }
}

object HeadlessAudio {

    private var applied = false

    fun tick(client: net.minecraft.client.Minecraft) {
        if (applied || client.options == null) return
        apply()
    }

    fun apply() {
        if (applied || !HeadlessMode.isEnabled) return
        val options = runCatching { client.options }.getOrNull() ?: return
        applied = true
        runCatching {
            for (source in net.minecraft.sounds.SoundSource.entries) {
                options.getSoundSourceOptionInstance(source).set(0.0)
            }
        }.onFailure { Kiwi.logger.warn("Kiwi could not mute audio for the headless run", it) }
        Kiwi.logger.info("Kiwi headless mode: window hidden, audio muted")
    }
}

object KnowledgeBootstrap {

    private var attempts = 0
    private var delay = 0

    fun tick() {
        if (Knowledge.isLoaded) return
        if (client.level == null) return
        if (delay++ < WARMUP_TICKS) return
        if (attempts >= MAX_ATTEMPTS) return

        attempts++
        delay = 0
        try {
            Knowledge.load()
        } catch (e: Throwable) {
            Kiwi.logger.error("Kiwi failed to load knowledge (attempt $attempts)", e)
        }
    }

    fun ensureLoaded() {
        if (!Knowledge.isLoaded) Knowledge.load()
    }

    private const val WARMUP_TICKS = 10
    private const val MAX_ATTEMPTS = 3
}
