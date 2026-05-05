package org.kvxd.kiwi.recipe

import org.slf4j.LoggerFactory

class RecipeGraph {

    private val logger = LoggerFactory.getLogger("kiwi:RecipeGraph")

    private val adjacency = mutableMapOf<String, MutableList<RecipeEdge>>()

    data class RecipeEdge(
        val recipe: ParsedRecipe,
        val ingredientIdx: Int
    )

    fun build() {
        adjacency.clear()

        for (recipe in RecipeDatabase.allRecipes) {
            val resultId = recipe.resultId.removePrefix("minecraft:")
            adjacency.getOrPut(resultId) { mutableListOf() }

            for ((idx, slot) in recipe.ingredients.withIndex()) {
                if (slot.isEmpty()) continue
                for (ingredient in slot) {
                    val ingredientId = when (ingredient.kind) {
                        IngredientKind.ITEM -> ingredient.name.removePrefix("minecraft:")
                        IngredientKind.TAG -> ingredient.name
                    }
                    val edge = RecipeEdge(recipe, idx)
                    adjacency.getOrPut(ingredientId) { mutableListOf() }.add(edge)
                }
            }
        }

        logger.info("RecipeGraph built: ${adjacency.size} nodes")
    }

    fun findPathTo(
        targetId: String,
        available: Map<String, Int>,
        maxDepth: Int = 8
    ): List<ParsedRecipe>? {
        val target = targetId.removePrefix("minecraft:")
        if (available[target] ?: 0 > 0) return emptyList()

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<CraftPlanNode>()

        for (recipe in RecipeDatabase.recipesForResult(target)) {
            queue.add(CraftPlanNode(recipe, emptyList(), available.toMutableMap()))
        }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.path.size >= maxDepth) continue

            val key = node.recipe.id
            if (key in visited) continue
            visited.add(key)

            val missing = findMissingIngredients(node.recipe, node.simulatedInventory)
            if (missing.isEmpty()) {
                return node.path + node.recipe
            }

            for ((ingredient, needed) in missing) {
                val recipes = RecipeDatabase.recipesForResult(ingredient)
                if (recipes.isEmpty()) continue

                for (subRecipe in recipes) {
                    val newInventory = node.simulatedInventory.toMutableMap()
                    newInventory[subRecipe.resultId.removePrefix("minecraft:")] =
                        (newInventory[subRecipe.resultId.removePrefix("minecraft:")] ?: 0) + subRecipe.resultCount

                    queue.add(
                        CraftPlanNode(
                            recipe = subRecipe,
                            path = node.path + node.recipe,
                            simulatedInventory = newInventory
                        )
                    )
                }
            }
        }

        return null
    }

    fun findAllDependencies(
        targetId: String,
        available: Map<String, Int>,
        maxDepth: Int = 8
    ): Set<ParsedRecipe>? {
        val path = findPathTo(targetId, available, maxDepth) ?: return null
        return path.toSet()
    }

    private fun findMissingIngredients(
        recipe: ParsedRecipe,
        inventory: Map<String, Int>
    ): Map<String, Int> {
        val missing = mutableMapOf<String, Int>()
        val tempInv = inventory.toMutableMap()

        for (slot in recipe.ingredients) {
            if (slot.isEmpty()) continue
            var satisfied = false

            for (ingredient in slot) {
                when (ingredient.kind) {
                    IngredientKind.ITEM -> {
                        val id = ingredient.name.removePrefix("minecraft:")
                        val have = tempInv[id] ?: 0
                        if (have >= 1) {
                            tempInv[id] = have - 1
                            satisfied = true
                            break
                        }
                    }

                    IngredientKind.TAG -> {
                        val items = TagResolver.getItems(ingredient.name)
                        for (item in items) {
                            val id = item.removePrefix("minecraft:")
                            val have = tempInv[id] ?: 0
                            if (have >= 1) {
                                tempInv[id] = have - 1
                                satisfied = true
                                break
                            }
                        }
                        if (satisfied) break
                    }
                }
            }

            if (!satisfied) {
                val firstItem = slot.firstOrNull {
                    it.kind == IngredientKind.ITEM
                }?.name?.removePrefix("minecraft:") ?: slot.firstOrNull()
                    ?.let { it.name.removePrefix("#") } ?: "unknown"
                missing[firstItem] = (missing[firstItem] ?: 0) + 1
            }
        }

        return missing
    }

    private data class CraftPlanNode(
        val recipe: ParsedRecipe,
        val path: List<ParsedRecipe>,
        val simulatedInventory: MutableMap<String, Int>
    )
}
