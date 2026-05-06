package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.kvxd.kiwi.agent.RecipeLookup

class RecipeGameTests {
    @GameTest(maxTicks = 100)
    fun recipeExistsWoodenPickaxe(helper: GameTestHelper) = helper.runKiwiTest {
        val recipes = RecipeLookup.getRecipesFor("wooden_pickaxe")

        helper.assertThat(recipes.isNotEmpty()) {
            "no recipe for wooden_pickaxe"
        }
    }

    @GameTest(maxTicks = 100)
    fun recipeExistsStonePickaxe(helper: GameTestHelper) = helper.runKiwiTest {
        val recipes = RecipeLookup.getRecipesFor("stone_pickaxe")

        helper.assertThat(recipes.isNotEmpty()) {
            "no recipe for stone_pickaxe"
        }
    }
}
