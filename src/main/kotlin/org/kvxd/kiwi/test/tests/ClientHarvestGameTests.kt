package org.kvxd.kiwi.test.tests

import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.harvest.HarvestToolTier
import org.kvxd.kiwi.harvest.HarvestToolType
import org.kvxd.kiwi.test.checkClientTest
import org.kvxd.kiwi.test.requireNotNullClientTest

internal object ClientHarvestGameTests {

    fun runAll() {
        harvestDrops()
        harvestLookups()
    }

    private fun harvestDrops() {
        val stone = requireNotNullClientTest(HarvestDatabase.getForBlock("stone")) {
            "stone block has no harvest info"
        }
        checkClientTest(stone.primaryDropId == "cobblestone") {
            "expected drop=cobblestone, got drop=${stone.primaryDropId}"
        }

        val deepslate = requireNotNullClientTest(HarvestDatabase.getForBlock("deepslate")) {
            "deepslate has no harvest info"
        }
        checkClientTest(deepslate.primaryDropId == "cobbled_deepslate") {
            "expected drop=cobbled_deepslate, got=${deepslate.primaryDropId}"
        }

        val oakLog = requireNotNullClientTest(HarvestDatabase.getForBlock("oak_log")) {
            "oak_log has no harvest info"
        }
        checkClientTest(oakLog.isSelfDrop) {
            "expected self-drop, got drop=${oakLog.primaryDropId}"
        }

        val ironOre = requireNotNullClientTest(HarvestDatabase.getForBlock("iron_ore")) {
            "iron_ore has no harvest info"
        }
        checkClientTest(ironOre.primaryDropId == "raw_iron") {
            "expected raw_iron, got ${ironOre.primaryDropId}"
        }
        checkClientTest(!ironOre.isSelfDrop) {
            "iron_ore->raw_iron should not be self-drop"
        }

        val diamondOre = requireNotNullClientTest(HarvestDatabase.getForBlock("diamond_ore")) {
            "diamond_ore has no harvest info"
        }
        checkClientTest(diamondOre.requiresCorrectTool) {
            "diamond_ore should require correct tool"
        }
        checkClientTest(diamondOre.toolType == HarvestToolType.PICKAXE) {
            "expected PICKAXE, got ${diamondOre.toolType}"
        }
        checkClientTest(diamondOre.minTier >= HarvestToolTier.IRON) {
            "expected IRON+ tier, got ${diamondOre.minTier}"
        }
    }

    private fun harvestLookups() {
        val alternatives = HarvestDatabase.findBlockAlternatives("cobblestone")
        checkClientTest("stone" in alternatives) {
            "alternatives for cobblestone: $alternatives (missing stone)"
        }

        val sources = RecipeLookup.getHarvestSourcesForDrop("cobblestone")
        val sourceIds = sources.map { it.blockId }
        val stoneHarvest = requireNotNullClientTest(sources.firstOrNull { it.blockId == "stone" }) {
            "cobblestone harvest sources missing stone: $sourceIds"
        }
        checkClientTest(!stoneHarvest.isSelfDrop) {
            "stone->cobblestone should not be self-drop"
        }
        checkClientTest("stone" in sourceIds) {
            "cobblestone harvest sources missing stone: $sourceIds"
        }
        checkClientTest("cobblestone" in sourceIds) {
            "cobblestone harvest sources missing cobblestone block: $sourceIds"
        }
    }
}
