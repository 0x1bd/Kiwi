package org.kvxd.kiwi.knowledge

class Ingredient(
    val count: Int,
    val options: IntArray,
    val label: String
) {
    fun accepts(itemId: Int): Boolean {
        for (option in options) if (option == itemId) return true
        return false
    }

    override fun toString(): String = "$label x$count"
}

enum class CraftStation {
    HAND,
    CRAFTING_TABLE
}

class CraftRecipe(
    val id: String,
    val result: Int,
    val resultCount: Int,
    val ingredients: Array<Ingredient>,
    val station: CraftStation,
    val width: Int,
    val height: Int,
    val shapedSlots: Array<Ingredient?>
) {
    val isShaped: Boolean get() = width > 0 && height > 0

    override fun toString(): String = "${Ids.itemName(result)} x$resultCount <- ${ingredients.joinToString(" + ")}"
}

class SmeltRecipe(
    val id: String,
    val result: Int,
    val resultCount: Int,
    val input: Ingredient,
    val cookingTime: Int
) {
    override fun toString(): String = "${Ids.itemName(result)} <- smelt $input"
}

enum class ToolKind {
    NONE,
    ANY,
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SWORD,
    SHEARS
}

enum class ToolTier(val level: Int) {
    NONE(0),
    WOOD(1),
    STONE(2),
    IRON(3),
    DIAMOND(4),
    NETHERITE(5);

    companion object {
        fun atLeast(level: Int): ToolTier = entries.firstOrNull { it.level == level } ?: NONE
    }
}

class BlockHarvest(
    val block: Int,
    val drop: Int,
    val minCount: Int,
    val maxCount: Int,
    val tool: ToolKind,
    val tier: ToolTier,
    val requiresCorrectTool: Boolean
) {
    val isSelfDrop: Boolean get() = Ids.blockName(block) == Ids.itemName(drop)

    fun requiredToolItemName(): String? {
        if (!requiresCorrectTool) return null
        val suffix = when (tool) {
            ToolKind.PICKAXE -> "pickaxe"
            ToolKind.AXE -> "axe"
            ToolKind.SHOVEL -> "shovel"
            ToolKind.HOE -> "hoe"
            ToolKind.SWORD -> "sword"
            ToolKind.SHEARS -> return "shears"
            ToolKind.NONE, ToolKind.ANY -> return null
        }
        val prefix = when (tier) {
            ToolTier.NETHERITE -> "netherite"
            ToolTier.DIAMOND -> "diamond"
            ToolTier.IRON -> "iron"
            ToolTier.STONE -> "stone"
            else -> "wooden"
        }
        return "${prefix}_$suffix"
    }

    fun acceptableToolNames(): List<String> {
        if (!requiresCorrectTool) return emptyList()
        val suffix = when (tool) {
            ToolKind.PICKAXE -> "pickaxe"
            ToolKind.AXE -> "axe"
            ToolKind.SHOVEL -> "shovel"
            ToolKind.HOE -> "hoe"
            ToolKind.SWORD -> "sword"
            ToolKind.SHEARS -> return listOf("shears")
            ToolKind.NONE, ToolKind.ANY -> return emptyList()
        }
        val tiers = listOf("wooden" to 1, "stone" to 2, "iron" to 3, "diamond" to 4, "netherite" to 5)
        return tiers.filter { it.second >= tier.level }.map { "${it.first}_$suffix" }
    }
}
