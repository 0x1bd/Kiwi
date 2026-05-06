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
        val targetIds = resolveItemChoices(targetId)
        if (targetIds.any { (available[it] ?: 0) > 0 }) return emptyList()

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<CraftPlanNode>()

        for (target in targetIds) {
            for (recipe in RecipeDatabase.recipesForResult(target)) {
                queue.add(CraftPlanNode(recipe, emptyList(), available.toMutableMap()))
            }
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

            for (ingredient in missing) {
                for (candidateId in ingredient.itemIds) {
                    val recipes = RecipeDatabase.recipesForResult(candidateId)
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
    ): List<MissingIngredient> {
        val missing = mutableListOf<MissingIngredient>()
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
                val itemIds = slot.flatMap { ingredient ->
                    when (ingredient.kind) {
                        IngredientKind.ITEM -> listOf(ingredient.name.removePrefix("minecraft:"))
                        IngredientKind.TAG -> resolveItemChoices(ingredient.name)
                    }
                }.toCollection(linkedSetOf())
                missing.add(MissingIngredient(itemIds.ifEmpty { setOf("unknown") }))
            }
        }

        return missing
    }

    private fun resolveItemChoices(id: String): Set<String> {
        val resolved = TagResolver.getItems(id)
        if (resolved.isNotEmpty()) return resolved.mapTo(linkedSetOf()) { it.removePrefix("minecraft:") }
        return setOf(id.removePrefix("#").removePrefix("minecraft:"))
    }

    private data class MissingIngredient(
        val itemIds: Set<String>
    )

    private data class CraftPlanNode(
        val recipe: ParsedRecipe,
        val path: List<ParsedRecipe>,
        val simulatedInventory: MutableMap<String, Int>
    )
}
