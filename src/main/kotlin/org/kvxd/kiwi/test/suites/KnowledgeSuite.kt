package org.kvxd.kiwi.test.suites

import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.knowledge.CraftStation
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.knowledge.NO_ID
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check
import org.kvxd.kiwi.test.fromClient
import org.kvxd.kiwi.test.onClient
import org.kvxd.kiwi.test.checkNotNull

object KnowledgeSuite {

    fun run(world: BotTestWorld) {
        world.context.onClient {
            if (!Knowledge.isLoaded) Knowledge.load()
        }

        world.context.onClient {
            harvestFacts()
            recipeFacts()
            tagFacts()
            costFacts()
            performance()
        }
    }

    private fun harvestFacts() {
        val stone = checkNotNull(Knowledge.harvestOf(Blocks.STONE)) { "stone has no harvest entry" }
        check(Ids.itemName(stone.drop) == "cobblestone") { "stone should drop cobblestone, got ${Ids.itemName(stone.drop)}" }
        check(stone.requiresCorrectTool) { "stone should need a pickaxe" }

        val log = checkNotNull(Knowledge.harvestOf(Blocks.OAK_LOG)) { "oak_log has no harvest entry" }
        check(Ids.itemName(log.drop) == "oak_log") { "oak_log should self-drop, got ${Ids.itemName(log.drop)}" }
        check(!log.requiresCorrectTool) { "oak_log should not require a tool for drops" }

        val ironOre = checkNotNull(Knowledge.harvestOf(Blocks.IRON_ORE)) { "iron_ore has no harvest entry" }
        check(Ids.itemName(ironOre.drop) == "raw_iron") { "iron_ore should drop raw_iron, got ${Ids.itemName(ironOre.drop)}" }

        val cobbleSources = Knowledge.sourcesOf(Ids.item("cobblestone")).map { Ids.blockName(it.block) }
        check("stone" in cobbleSources) { "cobblestone sources missing stone: $cobbleSources" }

        val diamondOre = checkNotNull(Knowledge.harvestOf(Blocks.DIAMOND_ORE)) { "diamond_ore has no harvest entry" }
        check(diamondOre.acceptableToolNames().contains("iron_pickaxe")) {
            "diamond ore should accept an iron pickaxe, got ${diamondOre.acceptableToolNames()}"
        }
        check(!diamondOre.acceptableToolNames().contains("stone_pickaxe")) {
            "diamond ore must not accept a stone pickaxe"
        }
    }

    private fun recipeFacts() {
        val pickaxe = Knowledge.craftsFor(Ids.item("wooden_pickaxe"))
        check(pickaxe.isNotEmpty()) { "no recipe for wooden_pickaxe" }
        check(pickaxe.all { it.station == CraftStation.CRAFTING_TABLE }) {
            "wooden_pickaxe is a 3x3 recipe and needs a table"
        }

        val planks = Knowledge.craftsFor(Ids.item("oak_planks"))
        check(planks.isNotEmpty()) { "no recipe for oak_planks" }
        check(planks.any { it.station == CraftStation.HAND }) { "oak_planks should be hand craftable" }
        check(planks.first().resultCount == 4) { "oak_planks recipe should yield 4" }

        val sticks = Knowledge.craftsFor(Ids.item("stick"))
        check(sticks.isNotEmpty()) { "no recipe for stick" }
        val stickRecipe = sticks.first()
        check(stickRecipe.ingredients.isNotEmpty()) { "stick recipe has no ingredients" }
        check(stickRecipe.ingredients.first().options.contains(Ids.item("oak_planks"))) {
            "stick recipe should accept oak_planks"
        }

        val ironIngot = Knowledge.smeltsFor(Ids.item("iron_ingot"))
        check(ironIngot.isNotEmpty()) { "no smelting recipe for iron_ingot" }
        check(ironIngot.any { it.input.options.contains(Ids.item("raw_iron")) }) {
            "iron_ingot should be smeltable from raw_iron"
        }
    }

    private fun tagFacts() {
        val logs = Knowledge.tagItems("minecraft:logs")
        check(logs.contains(Ids.item("oak_log"))) { "#logs should contain oak_log" }
        check(logs.contains(Ids.item("spruce_log"))) { "#logs should contain spruce_log (nested tag resolution)" }

        val planks = Knowledge.tagItems("minecraft:planks")
        check(planks.contains(Ids.item("oak_planks"))) { "#planks should contain oak_planks" }
    }

    private fun costFacts() {
        val log = Knowledge.acquisitionCost(Ids.item("oak_log"))
        val planks = Knowledge.acquisitionCost(Ids.item("oak_planks"))
        val stick = Knowledge.acquisitionCost(Ids.item("stick"))
        val pickaxe = Knowledge.acquisitionCost(Ids.item("wooden_pickaxe"))
        val diamondPickaxe = Knowledge.acquisitionCost(Ids.item("diamond_pickaxe"))

        check(log.isFinite()) { "oak_log should have a finite cost" }
        check(planks.isFinite() && planks < pickaxe) { "planks ($planks) should be cheaper than a pickaxe ($pickaxe)" }
        check(stick.isFinite()) { "stick should be obtainable" }
        check(pickaxe < diamondPickaxe) {
            "a wooden pickaxe ($pickaxe) should be cheaper than a diamond one ($diamondPickaxe)"
        }
        check(Knowledge.acquisitionCost(NO_ID).isInfinite()) { "unknown items must cost infinity" }
    }

    private fun performance() {
        val started = System.nanoTime()
        var sink = 0.0
        repeat(200_000) {
            sink += Knowledge.acquisitionCost(Ids.item("oak_planks"))
            sink += Knowledge.craftsFor(Ids.item("stick")).size
        }
        val ms = (System.nanoTime() - started) / 1_000_000.0
        Kiwi.logger.info("Kiwi test: 400k knowledge lookups in ${"%.1f".format(ms)}ms (sink=$sink)")
        check(ms < 1500.0) { "knowledge lookups are too slow: ${"%.1f".format(ms)}ms for 400k queries" }
    }
}
