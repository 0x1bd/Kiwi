package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.Identifier

class RegistryGameTests {
    @GameTest(maxTicks = 100)
    fun stoneInRegistry(helper: GameTestHelper) = helper.runKiwiTest {
        val block = BuiltInRegistries.BLOCK.getOptional(
            Identifier.parse("minecraft:stone")
        ).orElse(null)

        helper.assertThat(block != null) {
            "minecraft:stone not in BLOCK registry"
        }
    }
}
