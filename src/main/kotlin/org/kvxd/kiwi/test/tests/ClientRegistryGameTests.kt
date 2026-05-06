package org.kvxd.kiwi.test.tests

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.kvxd.kiwi.test.checkClientTest

internal object ClientRegistryGameTests {

    fun runAll() {
        stoneInRegistry()
    }

    private fun stoneInRegistry() {
        val block = BuiltInRegistries.BLOCK.getOptional(
            Identifier.parse("minecraft:stone")
        ).orElse(null)

        checkClientTest(block != null) {
            "minecraft:stone not in BLOCK registry"
        }
    }
}
