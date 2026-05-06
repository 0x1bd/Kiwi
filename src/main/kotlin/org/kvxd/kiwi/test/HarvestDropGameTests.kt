package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.harvest.HarvestToolTier
import org.kvxd.kiwi.harvest.HarvestToolType

class HarvestDropGameTests {
    @GameTest(maxTicks = 100)
    fun stoneDropIsCobblestone(helper: GameTestHelper) = helper.runKiwiTest {
        val info = helper.assertNotNull(HarvestDatabase.getForBlock("stone")) {
            "stone block has no harvest info"
        }

        helper.assertThat(info.primaryDropId == "cobblestone") {
            "expected drop=cobblestone, got drop=${info.primaryDropId}"
        }
    }

    @GameTest(maxTicks = 100)
    fun deepslateDropIsCobbledDeepslate(helper: GameTestHelper) = helper.runKiwiTest {
        val info = helper.assertNotNull(HarvestDatabase.getForBlock("deepslate")) {
            "deepslate has no harvest info"
        }

        helper.assertThat(info.primaryDropId == "cobbled_deepslate") {
            "expected drop=cobbled_deepslate, got=${info.primaryDropId}"
        }
    }

    @GameTest(maxTicks = 100)
    fun oakLogIsSelfDrop(helper: GameTestHelper) = helper.runKiwiTest {
        val info = helper.assertNotNull(HarvestDatabase.getForBlock("oak_log")) {
            "oak_log has no harvest info"
        }

        helper.assertThat(info.isSelfDrop) {
            "expected self-drop, got drop=${info.primaryDropId}"
        }
    }

    @GameTest(maxTicks = 100)
    fun ironOreDropsRawIron(helper: GameTestHelper) = helper.runKiwiTest {
        val info = helper.assertNotNull(HarvestDatabase.getForBlock("iron_ore")) {
            "iron_ore has no harvest info"
        }

        helper.assertThat(info.primaryDropId == "raw_iron") {
            "expected raw_iron, got ${info.primaryDropId}"
        }
        helper.assertThat(!info.isSelfDrop) {
            "iron_ore->raw_iron should not be self-drop"
        }
    }

    @GameTest(maxTicks = 100)
    fun diamondOreToolRequirements(helper: GameTestHelper) = helper.runKiwiTest {
        val info = helper.assertNotNull(HarvestDatabase.getForBlock("diamond_ore")) {
            "diamond_ore has no harvest info"
        }

        helper.assertThat(info.requiresCorrectTool) {
            "diamond_ore should require correct tool"
        }
        helper.assertThat(info.toolType == HarvestToolType.PICKAXE) {
            "expected PICKAXE, got ${info.toolType}"
        }
        helper.assertThat(info.minTier >= HarvestToolTier.IRON) {
            "expected IRON+ tier, got ${info.minTier}"
        }
    }
}
