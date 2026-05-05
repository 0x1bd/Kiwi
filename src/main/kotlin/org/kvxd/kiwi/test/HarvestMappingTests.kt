package org.kvxd.kiwi.test

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.harvest.HarvestToolTier
import org.kvxd.kiwi.harvest.HarvestToolType
import org.kvxd.kiwi.recipe.TagResolver

class HarvestMappingTests : TestRunner.TestSuite("Harvest Mapping") {

    init {
        test("stone_drop_is_cobblestone") {
            val info = HarvestDatabase.getForBlock("stone")
                ?: throw AssertionError("stone block has no harvest info")
            check(info.primaryDropId == "cobblestone") {
                "expected drop=cobblestone, got drop=${info.primaryDropId}"
            }
        }

        test("deepslate_drop_is_cobbled_deepslate") {
            val info = HarvestDatabase.getForBlock("deepslate")
                ?: throw AssertionError("deepslate has no harvest info")
            check(info.primaryDropId == "cobbled_deepslate") {
                "expected drop=cobbled_deepslate, got=${info.primaryDropId}"
            }
        }

        test("oak_log_is_self_drop") {
            val info = HarvestDatabase.getForBlock("oak_log")
                ?: throw AssertionError("oak_log has no harvest info")
            check(info.isSelfDrop) { "expected self-drop, got drop=${info.primaryDropId}" }
        }

        test("cobblestone_harvest_maps_to_stone") {
            val harvest = RecipeLookup.getHarvestByDrop("cobblestone")
                ?: throw AssertionError("getHarvestByDrop(cobblestone) returned null")
            check(harvest.blockId == "stone") {
                "harvest blockId should be stone, got ${harvest.blockId}"
            }
            check(!harvest.isSelfDrop) {
                "stone→cobblestone should not be self-drop"
            }
        }

        test("findBlockAlternatives_includes_stone") {
            val alts = HarvestDatabase.findBlockAlternatives("cobblestone")
            check("stone" in alts) {
                "alternatives for cobblestone: $alts (missing stone)"
            }
        }

        test("cobblestone_harvest_sources_include_stone_and_cobblestone") {
            val sources = RecipeLookup.getHarvestSourcesForDrop("cobblestone").map { it.blockId }
            check("stone" in sources) {
                "cobblestone harvest sources missing stone: $sources"
            }
            check("cobblestone" in sources) {
                "cobblestone harvest sources missing cobblestone block: $sources"
            }
        }

        test("iron_ore_drops_raw_iron") {
            val info = HarvestDatabase.getForBlock("iron_ore")
                ?: throw AssertionError("iron_ore has no harvest info")
            check(info.primaryDropId == "raw_iron") {
                "expected raw_iron, got ${info.primaryDropId}"
            }
            check(!info.isSelfDrop) { "iron_ore→raw_iron should not be self-drop" }
        }

        test("diamond_ore_tool_requirements") {
            val info = HarvestDatabase.getForBlock("diamond_ore")
                ?: throw AssertionError("diamond_ore has no harvest info")
            check(info.requiresCorrectTool) { "diamond_ore should require correct tool" }
            check(info.toolType == HarvestToolType.PICKAXE) {
                "expected PICKAXE, got ${info.toolType}"
            }
            check(info.minTier >= HarvestToolTier.IRON) {
                "expected IRON+ tier, got ${info.minTier}"
            }
        }

        test("stone_tool_materials_tag_resolves") {
            val items = TagResolver.getItems("#minecraft:stone_tool_materials")
            check(items.isNotEmpty()) { "tag resolves to empty set" }
            check(items.any { it.removePrefix("minecraft:") == "cobblestone" }) {
                "tag missing cobblestone: $items"
            }
        }

        test("stone_tool_materials_expand_to_harvestable_block_sources") {
            val items = TagResolver.getItems("#minecraft:stone_tool_materials")
                .map { it.removePrefix("minecraft:") }
            val blockSources = items.flatMap { RecipeLookup.findBlockAlternatives(it) }.toSet()
            check("stone" in blockSources) {
                "stone tool material block sources missing stone: $blockSources"
            }
            check("cobblestone" in blockSources) {
                "stone tool material block sources missing cobblestone: $blockSources"
            }
        }

        test("stone_in_registry") {
            val block = BuiltInRegistries.BLOCK.getOptional(
                Identifier.parse("minecraft:stone")
            ).orElse(null)
            check(block != null) { "minecraft:stone not in BLOCK registry" }
        }

        test("recipe_exists_wooden_pickaxe") {
            val recipes = RecipeLookup.getRecipesFor("wooden_pickaxe")
            check(recipes.isNotEmpty()) { "no recipe for wooden_pickaxe" }
        }

        test("recipe_exists_stone_pickaxe") {
            val recipes = RecipeLookup.getRecipesFor("stone_pickaxe")
            check(recipes.isNotEmpty()) { "no recipe for stone_pickaxe" }
        }
    }
}

private fun check(value: Boolean, lazyMessage: () -> Any) {
    if (!value) throw AssertionError(lazyMessage().toString())
}
