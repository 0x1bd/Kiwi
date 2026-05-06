package org.kvxd.kiwi.recipe

enum class ParsedRecipeKind {
    SHAPED,
    SHAPELESS,
    SMELTING,
    BLASTING,
    SMOKING,
    CAMPFIRE_COOKING
}

enum class IngredientKind {
    ITEM,
    TAG
}

data class ParsedIngredient(
    val kind: IngredientKind,
    val name: String
) {
    val asItemId: String get() = if (kind == IngredientKind.ITEM) name else ""

    val asTagId: String get() = if (kind == IngredientKind.TAG) name else ""

    val displayName: String
        get() = when (kind) {
            IngredientKind.ITEM -> name.substringAfterLast("/").substringAfterLast(":")
            IngredientKind.TAG -> name.substringAfterLast("/").substringAfterLast(":")
        }
}

data class ParsedRecipe(
    val id: String,
    val kind: ParsedRecipeKind,
    val resultId: String,
    val resultCount: Int,
    val ingredients: List<List<ParsedIngredient>>,
    val width: Int,
    val height: Int,
    val source: String,
    val cookingTime: Int,
    val experience: Float
) {
    val flatIngredients: List<ParsedIngredient>
        get() = ingredients.flatten().filter { it.kind != IngredientKind.TAG || it.asTagId.isNotEmpty() }

    val ingredientIds: Set<String>
        get() = flatIngredients.map { it.name }.toSet()

    val isShaped: Boolean get() = kind == ParsedRecipeKind.SHAPED

    val isShapeless: Boolean get() = kind == ParsedRecipeKind.SHAPELESS

    val isCooking: Boolean
        get() = kind == ParsedRecipeKind.SMELTING ||
                kind == ParsedRecipeKind.BLASTING ||
                kind == ParsedRecipeKind.SMOKING ||
                kind == ParsedRecipeKind.CAMPFIRE_COOKING

    val isCrafting: Boolean get() = isShaped || isShapeless

    val gridSlots: List<ParsedIngredient?>
        get() {
            if (!isShaped) return flatIngredients.map { it }
            val slots = mutableListOf<ParsedIngredient?>()
            for (r in 0 until height) {
                for (c in 0 until width) {
                    val idx = r * width + c
                    slots.add(ingredients.getOrNull(idx)?.firstOrNull())
                }
            }
            return slots
        }
}
