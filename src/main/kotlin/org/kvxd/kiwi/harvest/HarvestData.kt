package org.kvxd.kiwi.harvest

import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.util.registryPath

enum class HarvestToolType {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SWORD,
    SHEARS,
    ANY,
    NONE
}

enum class HarvestToolTier(val level: Int) {
    NONE(0),
    WOOD(1),
    STONE(2),
    IRON(3),
    DIAMOND(4),
    NETHERITE(5);

    companion object {
        fun forLevel(level: Int): HarvestToolTier {
            return entries.find { it.level == level } ?: NONE
        }
    }
}

data class BlockHarvestInfo(
    val block: Block,
    val primaryDropId: String,
    val dropCount: IntRange,
    val toolType: HarvestToolType,
    val minTier: HarvestToolTier,
    val requiresCorrectTool: Boolean
) {
    val blockId: String get() = block.registryPath

    val isSelfDrop: Boolean get() = blockId == primaryDropId

    fun bestToolItemId(): String? {
        if (toolType == HarvestToolType.NONE || toolType == HarvestToolType.ANY) return null
        val tierPrefix = when {
            minTier >= HarvestToolTier.NETHERITE -> "netherite"
            minTier >= HarvestToolTier.DIAMOND -> "diamond"
            minTier >= HarvestToolTier.IRON -> "iron"
            minTier >= HarvestToolTier.STONE -> "stone"
            else -> "wooden"
        }
        val toolName = when (toolType) {
            HarvestToolType.PICKAXE -> "pickaxe"
            HarvestToolType.AXE -> "axe"
            HarvestToolType.SHOVEL -> "shovel"
            HarvestToolType.HOE -> "hoe"
            HarvestToolType.SWORD -> "sword"
            HarvestToolType.SHEARS -> "shears"
            HarvestToolType.ANY, HarvestToolType.NONE -> return null
        }
        return "minecraft:${tierPrefix}_$toolName"
    }
}
