package org.kvxd.kiwi.harvest

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HarvestDataTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `tool tier level order`() {
        assertTrue(HarvestToolTier.NONE.level < HarvestToolTier.WOOD.level)
        assertTrue(HarvestToolTier.WOOD.level < HarvestToolTier.STONE.level)
        assertTrue(HarvestToolTier.STONE.level < HarvestToolTier.IRON.level)
        assertTrue(HarvestToolTier.IRON.level < HarvestToolTier.DIAMOND.level)
        assertTrue(HarvestToolTier.DIAMOND.level < HarvestToolTier.NETHERITE.level)
    }

    @Test
    fun `tool tier forLevel`() {
        assertEquals(HarvestToolTier.NONE, HarvestToolTier.forLevel(0))
        assertEquals(HarvestToolTier.WOOD, HarvestToolTier.forLevel(1))
        assertEquals(HarvestToolTier.STONE, HarvestToolTier.forLevel(2))
        assertEquals(HarvestToolTier.IRON, HarvestToolTier.forLevel(3))
        assertEquals(HarvestToolTier.DIAMOND, HarvestToolTier.forLevel(4))
        assertEquals(HarvestToolTier.NETHERITE, HarvestToolTier.forLevel(5))
        assertEquals(HarvestToolTier.NONE, HarvestToolTier.forLevel(99))
    }

    @Test
    fun `BlockHarvestInfo isSelfDrop`() {
        val selfDrop = BlockHarvestInfo(
            block = Blocks.OAK_LOG,
            primaryDropId = "oak_log",
            dropCount = 1..1,
            toolType = HarvestToolType.AXE,
            minTier = HarvestToolTier.WOOD,
            requiresCorrectTool = false
        )
        assertTrue(selfDrop.isSelfDrop)

        val nonSelfDrop = BlockHarvestInfo(
            block = Blocks.STONE,
            primaryDropId = "cobblestone",
            dropCount = 1..1,
            toolType = HarvestToolType.PICKAXE,
            minTier = HarvestToolTier.WOOD,
            requiresCorrectTool = true
        )
        assertFalse(nonSelfDrop.isSelfDrop)
    }

    @Test
    fun `bestToolItemId for pickaxe tiers`() {
        val diamondPick = BlockHarvestInfo(
            block = Blocks.DIAMOND_ORE,
            primaryDropId = "raw_diamond",
            dropCount = 1..1,
            toolType = HarvestToolType.PICKAXE,
            minTier = HarvestToolTier.IRON,
            requiresCorrectTool = true
        )
        assertEquals("minecraft:iron_pickaxe", diamondPick.bestToolItemId())

        val netheritePick = BlockHarvestInfo(
            block = Blocks.ANCIENT_DEBRIS,
            primaryDropId = "ancient_debris",
            dropCount = 1..1,
            toolType = HarvestToolType.PICKAXE,
            minTier = HarvestToolTier.DIAMOND,
            requiresCorrectTool = true
        )
        assertEquals("minecraft:diamond_pickaxe", netheritePick.bestToolItemId())
    }

    @Test
    fun `bestToolItemId returns null for NONE and ANY`() {
        val noTool = BlockHarvestInfo(
            block = Blocks.DIRT,
            primaryDropId = "dirt",
            dropCount = 1..1,
            toolType = HarvestToolType.NONE,
            minTier = HarvestToolTier.NONE,
            requiresCorrectTool = false
        )
        assertNull(noTool.bestToolItemId())

        val anyTool = BlockHarvestInfo(
            block = Blocks.GLASS,
            primaryDropId = "glass",
            dropCount = 1..1,
            toolType = HarvestToolType.ANY,
            minTier = HarvestToolTier.NONE,
            requiresCorrectTool = false
        )
        assertNull(anyTool.bestToolItemId())
    }

    @Test
    fun `bestToolItemId for shears`() {
        val shears = BlockHarvestInfo(
            block = Blocks.COBWEB,
            primaryDropId = "string",
            dropCount = 1..1,
            toolType = HarvestToolType.SHEARS,
            minTier = HarvestToolTier.NONE,
            requiresCorrectTool = false
        )
        assertEquals("minecraft:wooden_shears", shears.bestToolItemId())
    }
}