package org.kvxd.kiwi.test.tests

import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.recipe.TagResolver
import org.kvxd.kiwi.test.checkClientTest

internal object ClientTagResolverGameTests {

    fun runAll() {
        stoneToolMaterialsTagResolves()
        stoneToolMaterialsExpandToHarvestableBlockSources()
    }

    private fun stoneToolMaterialsTagResolves() {
        val items = TagResolver.getItems("#minecraft:stone_tool_materials")

        checkClientTest(items.isNotEmpty()) {
            "tag resolves to empty set"
        }
        checkClientTest(items.any { it.removePrefix("minecraft:") == "cobblestone" }) {
            "tag missing cobblestone: $items"
        }
    }

    private fun stoneToolMaterialsExpandToHarvestableBlockSources() {
        val items = TagResolver.getItems("#minecraft:stone_tool_materials")
            .map { it.removePrefix("minecraft:") }
        val blockSources = items.flatMap { RecipeLookup.findBlockAlternatives(it) }.toSet()

        checkClientTest(Blocks.STONE in blockSources) {
            "stone tool material block sources missing stone: $blockSources"
        }
        checkClientTest(Blocks.COBBLESTONE in blockSources) {
            "stone tool material block sources missing cobblestone: $blockSources"
        }
    }
}
