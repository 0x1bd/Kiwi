package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.test.suites.KnowledgeSuite
import org.kvxd.kiwi.test.suites.MiningSuite
import org.kvxd.kiwi.test.suites.NavigationSuite
import org.kvxd.kiwi.test.suites.PlacementSuite
import org.kvxd.kiwi.test.suites.PlanningSuite
import org.kvxd.kiwi.test.suites.VoxelSuite

@Suppress("UnstableApiUsage")
class KiwiClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        Kiwi.logger.info("Kiwi client game tests: starting")

        context.worldBuilder().create().use { singleplayer ->
            singleplayer.clientLevel.waitForChunksDownload()

            val world = BotTestWorld(context, singleplayer)
            prepare(world)

            runSuite("knowledge") { KnowledgeSuite.run(world) }
            runSuite("voxel") { VoxelSuite.run(world) }
            runSuite("navigation") { NavigationSuite.run(world) }
            runSuite("mining") { MiningSuite.run(world) }
            runSuite("placement") { PlacementSuite.run(world) }
            runSuite("planning") { PlanningSuite.run(world) }

            context.onClient { Bot.stop() }
        }

        Kiwi.logger.info("Kiwi client game tests: all suites passed")
    }

    private fun prepare(world: BotTestWorld) {
        world.command("gamerule doTileDrops true")
        world.command("gamerule doImmediateRespawn true")
        world.command("gamerule keepInventory true")
        world.command("gamerule doWeatherCycle false")
        world.command("difficulty peaceful")
        world.command("time set day")
        world.gamemode("survival")
        world.context.onClient { client ->
            ConfigData.debugMode = true
            ConfigData.allowBreak = true
            ConfigData.allowPillar = true
            runCatching { client.options.renderDistance().set(12) }
            runCatching { client.options.simulationDistance().set(12) }
        }
        world.settle(10)
    }

    private inline fun runSuite(name: String, body: () -> Unit) {
        Kiwi.logger.info("Kiwi client game tests: running '$name'")
        val started = System.nanoTime()
        body()
        Kiwi.logger.info(
            "Kiwi client game tests: '$name' passed in ${"%.1f".format((System.nanoTime() - started) / 1_000_000.0)}ms"
        )
    }
}
