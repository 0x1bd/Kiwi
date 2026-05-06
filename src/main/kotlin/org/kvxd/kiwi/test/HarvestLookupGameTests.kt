package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.harvest.HarvestDatabase

class HarvestLookupGameTests {
    @GameTest(maxTicks = 100)
    fun cobblestoneHarvestMapsToStone(helper: GameTestHelper) = helper.runKiwiTest {
        val harvest = helper.assertNotNull(RecipeLookup.getHarvestByDrop("cobblestone")) {
            "getHarvestByDrop(cobblestone) returned null"
        }

        helper.assertThat(harvest.blockId == "stone") {
            "harvest blockId should be stone, got ${harvest.blockId}"
        }
        helper.assertThat(!harvest.isSelfDrop) {
            "stone->cobblestone should not be self-drop"
        }
    }

    @GameTest(maxTicks = 100)
    fun findBlockAlternativesIncludesStone(helper: GameTestHelper) = helper.runKiwiTest {
        val alternatives = HarvestDatabase.findBlockAlternatives("cobblestone")

        helper.assertThat("stone" in alternatives) {
            "alternatives for cobblestone: $alternatives (missing stone)"
        }
    }

    @GameTest(maxTicks = 100)
    fun cobblestoneHarvestSourcesIncludeStoneAndCobblestone(helper: GameTestHelper) = helper.runKiwiTest {
        val sources = RecipeLookup.getHarvestSourcesForDrop("cobblestone").map { it.blockId }

        helper.assertThat("stone" in sources) {
            "cobblestone harvest sources missing stone: $sources"
        }
        helper.assertThat("cobblestone" in sources) {
            "cobblestone harvest sources missing cobblestone block: $sources"
        }
    }
}
