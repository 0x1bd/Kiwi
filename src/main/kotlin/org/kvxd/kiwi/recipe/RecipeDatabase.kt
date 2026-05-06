package org.kvxd.kiwi.recipe

import org.kvxd.kiwi.data.VanillaDataFiles
import org.slf4j.LoggerFactory

object RecipeDatabase {

    private val logger = LoggerFactory.getLogger("kiwi:RecipeDatabase")

    private val recipes = mutableListOf<ParsedRecipe>()
    private val byResult = mutableMapOf<String, MutableList<ParsedRecipe>>()
    private val byIngredient = mutableMapOf<String, MutableList<ParsedRecipe>>()

    val allRecipes: List<ParsedRecipe> get() = recipes.toList()

    val isLoaded: Boolean get() = recipes.isNotEmpty()

    fun load() {
        recipes.clear()
        byResult.clear()
        byIngredient.clear()

        val recipeInputs = VanillaDataFiles.jsonInputs("recipes")
        val tagInputs = VanillaDataFiles.jsonInputs("tags/items")

        if (tagInputs.isNotEmpty()) {
            TagResolver.load(tagInputs)
        } else {
            logger.warn("No item tag JSONs found - tag-based ingredients won't be resolved")
        }

        if (recipeInputs.isEmpty()) {
            logger.warn("No vanilla recipe JSONs found in runtime data cache")
            return
        }

        val parsed = RecipeJsonParser.parseAll(recipeInputs)

        for (recipe in parsed) {
            recipes.add(recipe)
            byResult.getOrPut(recipe.resultId) { mutableListOf() }.add(recipe)

            for (ingredient in recipe.flatIngredients) {
                when (ingredient.kind) {
                    IngredientKind.ITEM -> {
                        byIngredient.getOrPut(ingredient.name) { mutableListOf() }.add(recipe)
                    }
                    IngredientKind.TAG -> {
                        val items = TagResolver.getItems(ingredient.name)
                        for (item in items) {
                            byIngredient.getOrPut(item) { mutableListOf() }.add(recipe)
                        }
                    }
                }
            }
        }

        logger.info("RecipeDatabase loaded: ${recipes.size} recipes, ${byResult.size} results, ${byIngredient.size} ingredient keys")
    }

    fun recipesForResult(resultId: String): List<ParsedRecipe> {
        val exact = resultId
        val withNamespace = if (exact.contains(":")) exact else "minecraft:$exact"
        val withoutNamespace = exact.removePrefix("minecraft:")

        return byResult[withNamespace] ?: byResult[withoutNamespace] ?: byResult[exact] ?: emptyList()
    }

    fun recipesUsingIngredient(itemId: String): List<ParsedRecipe> {
        val exact = itemId
        val withNamespace = if (exact.contains(":")) exact else "minecraft:$exact"
        val withoutNamespace = exact.removePrefix("minecraft:")

        return byIngredient[withNamespace] ?: byIngredient[withoutNamespace] ?: byIngredient[exact] ?: emptyList()
    }

    fun findCraftableRecipes(
        availableItems: Set<String>,
        availableCounts: Map<String, Int> = emptyMap()
    ): List<ParsedRecipe> {
        return recipes.filter { recipe ->
            recipe.ingredients.all { slot ->
                slot.any { ingredient ->
                    when (ingredient.kind) {
                        IngredientKind.ITEM -> {
                            val id = ingredient.name.removePrefix("minecraft:")
                            val count = availableCounts[id] ?: (if (id in availableItems) 1 else 0)
                            count > 0
                        }
                        IngredientKind.TAG -> {
                            val items = TagResolver.getItems(ingredient.name)
                            items.any { it.removePrefix("minecraft:") in availableItems }
                        }
                    }
                }
            }
        }
    }

    fun canCraft(recipe: ParsedRecipe, available: Map<String, Int>): Boolean {
        for (slot in recipe.ingredients) {
            if (slot.isEmpty()) continue
            val satisfied = slot.any { ingredient ->
                when (ingredient.kind) {
                    IngredientKind.ITEM -> {
                        val id = ingredient.name.removePrefix("minecraft:")
                        (available[id] ?: 0) >= 1
                    }
                    IngredientKind.TAG -> {
                        val items = TagResolver.getItems(ingredient.name)
                        items.sumOf { available[it.removePrefix("minecraft:")] ?: 0 } >= 1
                    }
                }
            }
            if (!satisfied) return false
        }
        return true
    }

    fun dumpStats() {
        val byKind = recipes.groupBy { it.kind }
        logger.info("Recipe database stats:")
        logger.info("  Total: ${recipes.size}")
        byKind.forEach { (kind, list) -> logger.info("  $kind: ${list.size}") }
        logger.info("  Unique results: ${byResult.size}")
        logger.info("  Unique ingredients: ${byIngredient.size}")
    }

}
