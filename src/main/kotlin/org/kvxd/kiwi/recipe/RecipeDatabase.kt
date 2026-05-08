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
        val baseInventory = availableInventory(availableItems, availableCounts)
        return recipes.filter { recipe ->
            canCraftWithNormalizedInventory(recipe, baseInventory)
        }
    }

    fun canCraft(recipe: ParsedRecipe, available: Map<String, Int>): Boolean {
        return canCraftWithNormalizedInventory(recipe, normalizeInventory(available))
    }

    private fun canCraftWithNormalizedInventory(recipe: ParsedRecipe, available: Map<String, Int>): Boolean {
        val simulated = available.toMutableMap()
        for (slot in recipe.ingredients) {
            if (slot.isEmpty()) continue
            if (!consumeSlot(slot, simulated)) return false
        }
        return true
    }

    private fun availableInventory(
        availableItems: Set<String>,
        availableCounts: Map<String, Int>
    ): Map<String, Int> {
        val inventory = normalizeInventory(availableCounts).toMutableMap()
        for (item in availableItems) {
            val id = normalizeItemId(item)
            if ((inventory[id] ?: 0) <= 0) {
                inventory[id] = 1
            }
        }
        return inventory
    }

    private fun normalizeInventory(available: Map<String, Int>): Map<String, Int> {
        val normalized = mutableMapOf<String, Int>()
        for ((id, count) in available) {
            if (count <= 0) continue
            val cleanId = normalizeItemId(id)
            normalized[cleanId] = (normalized[cleanId] ?: 0) + count
        }
        return normalized
    }

    private fun consumeSlot(
        slot: List<ParsedIngredient>,
        inventory: MutableMap<String, Int>
    ): Boolean {
        val choices = slot
            .flatMap { ingredientChoices(it) }
            .distinct()
            .sortedByDescending { inventory[it] ?: 0 }

        for (id in choices) {
            val have = inventory[id] ?: 0
            if (have <= 0) continue
            inventory[id] = have - 1
            return true
        }

        return false
    }

    private fun ingredientChoices(ingredient: ParsedIngredient): List<String> {
        return when (ingredient.kind) {
            IngredientKind.ITEM -> listOf(normalizeItemId(ingredient.name))
            IngredientKind.TAG -> {
                val resolved = TagResolver.getItems(ingredient.name)
                if (resolved.isNotEmpty()) resolved.map { normalizeItemId(it) }
                else listOf(normalizeItemId(ingredient.name))
            }
        }
    }

    private fun normalizeItemId(itemId: String): String =
        itemId.removePrefix("#").removePrefix("minecraft:")

    fun dumpStats() {
        val byKind = recipes.groupBy { it.kind }
        logger.info("Recipe database stats:")
        logger.info("  Total: ${recipes.size}")
        byKind.forEach { (kind, list) -> logger.info("  $kind: ${list.size}") }
        logger.info("  Unique results: ${byResult.size}")
        logger.info("  Unique ingredients: ${byIngredient.size}")
    }

}
