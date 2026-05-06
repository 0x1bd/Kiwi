package org.kvxd.kiwi.test.tests

import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.test.checkClientTest

internal object ClientRecipeGameTests {

    fun runAll() {
        recipeExists("wooden_pickaxe")
        recipeExists("stone_pickaxe")
    }

    private fun recipeExists(itemId: String) {
        val recipes = RecipeLookup.getRecipesFor(itemId)
        checkClientTest(recipes.isNotEmpty()) {
            "no recipe for $itemId"
        }
    }
}
