package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.test.tests.ClientHarvestGameTests
import org.kvxd.kiwi.test.tests.ClientRecipeGameTests
import org.kvxd.kiwi.test.tests.ClientRegistryGameTests
import org.kvxd.kiwi.test.tests.ClientTagResolverGameTests

@Suppress("UnstableApiUsage")
class KiwiClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        Kiwi.logger.info("Kiwi client game tests: starting")

        GameTestSupport.ensureInitialized()
        context.waitTick()

        ClientRegistryGameTests.runAll()
        ClientRecipeGameTests.runAll()
        ClientTagResolverGameTests.runAll()
        ClientHarvestGameTests.runAll()

        Kiwi.logger.info("Kiwi client game tests: passed")
    }
}
