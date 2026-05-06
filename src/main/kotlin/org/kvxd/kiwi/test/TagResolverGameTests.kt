package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.recipe.TagResolver

class TagResolverGameTests {
    @GameTest(maxTicks = 100)
    fun stoneToolMaterialsTagResolves(helper: GameTestHelper) = helper.runKiwiTest {
        val items = TagResolver.getItems("#minecraft:stone_tool_materials")

        helper.assertThat(items.isNotEmpty()) {
            "tag resolves to empty set"
        }
        helper.assertThat(items.any { it.removePrefix("minecraft:") == "cobblestone" }) {
            "tag missing cobblestone: $items"
        }
    }

    @GameTest(maxTicks = 100)
    fun stoneToolMaterialsExpandToHarvestableBlockSources(helper: GameTestHelper) = helper.runKiwiTest {
        val items = TagResolver.getItems("#minecraft:stone_tool_materials")
            .map { it.removePrefix("minecraft:") }
        val blockSources = items.flatMap { RecipeLookup.findBlockAlternatives(it) }.toSet()

        helper.assertThat("stone" in blockSources) {
            "stone tool material block sources missing stone: $blockSources"
        }
        helper.assertThat("cobblestone" in blockSources) {
            "stone tool material block sources missing cobblestone: $blockSources"
        }
    }
}
