package org.kvxd.kiwi.recipe

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecipeDatabaseTest {

    @BeforeEach
    fun loadDatabase() {
        RecipeDatabase.load()
    }

    @AfterEach
    fun restoreDatabase() {
        RecipeDatabase.load()
    }

    @Test
    fun `database loads recipes`() {
        assertTrue(RecipeDatabase.isLoaded)
        assertTrue(RecipeDatabase.allRecipes.isNotEmpty())
    }

    @Test
    fun `finds recipes by result ID`() {
        val recipes = RecipeDatabase.recipesForResult("wooden_pickaxe")
        assertTrue(recipes.isNotEmpty(), "Expected at least one recipe for wooden_pickaxe")
        assertTrue(recipes.any { it.resultId == "minecraft:wooden_pickaxe" })
    }

    @Test
    fun `finds recipes by result ID with namespace`() {
        val recipes = RecipeDatabase.recipesForResult("minecraft:wooden_pickaxe")
        assertTrue(recipes.isNotEmpty())
    }

    @Test
    fun `handles missing recipes gracefully`() {
        val recipes = RecipeDatabase.recipesForResult("nonexistent_item_xyzzy")
        assertTrue(recipes.isEmpty())
    }

    @Test
    fun `has both crafting and cooking recipes`() {
        val crafting = RecipeDatabase.allRecipes.filter { it.isCrafting }
        val cooking = RecipeDatabase.allRecipes.filter { it.isCooking }
        assertTrue(crafting.isNotEmpty(), "Expected at least one crafting recipe")
        assertTrue(cooking.isNotEmpty(), "Expected at least one cooking recipe")
    }

    @Test
    fun `all recipes have result IDs`() {
        for (recipe in RecipeDatabase.allRecipes) {
            assertFalse(recipe.resultId.isEmpty(), "Recipe ${recipe.id} has empty resultId")
        }
    }

    @Test
    fun `shaped recipes have valid grid dimensions`() {
        val shaped = RecipeDatabase.allRecipes.filter { it.isShaped }
        for (recipe in shaped) {
            assertTrue(recipe.width > 0, "Shaped recipe ${recipe.id} has width ${recipe.width}")
            assertTrue(recipe.height > 0, "Shaped recipe ${recipe.id} has height ${recipe.height}")
        }
    }

    @Test
    fun `cooking recipes have positive cooking time`() {
        val cooking = RecipeDatabase.allRecipes.filter { it.isCooking }
        for (recipe in cooking) {
            assertTrue(recipe.cookingTime > 0, "Cooking recipe ${recipe.id} has cookingTime ${recipe.cookingTime}")
        }
    }

    @Test
    fun `finds recipes using ingredient`() {
        val recipes = RecipeDatabase.recipesUsingIngredient("stick")
        assertTrue(recipes.isNotEmpty(), "Expected recipes using stick")
    }

    @Test
    fun `canCraft checks availability`() {
        val recipes = RecipeDatabase.recipesForResult("stick")
        if (recipes.isNotEmpty()) {
            val recipe = recipes.first()
            val available = mutableMapOf<String, Int>()
            for (ingredient in recipe.flatIngredients) {
                val id = ingredient.name.removePrefix("minecraft:")
                available[id] = 10
                if (ingredient.kind == IngredientKind.TAG) {
                    for (item in TagResolver.getItems(ingredient.name)) {
                        available[item.removePrefix("minecraft:")] = 10
                    }
                }
            }
            assertTrue(RecipeDatabase.canCraft(recipe, available))
        }
    }

    @Test
    fun `findCraftableRecipes filters by availability`() {
        val empty = emptyMap<String, Int>()
        val results = RecipeDatabase.findCraftableRecipes(emptySet(), empty)
        assertTrue(results.isEmpty(), "No recipes should be craftable with empty inventory")
    }

    @Test
    fun `canCraft consumes duplicate item ingredients`() {
        val recipe = ParsedRecipe(
            id = "test_duplicate_sticks",
            kind = ParsedRecipeKind.SHAPELESS,
            resultId = "minecraft:test_result",
            resultCount = 1,
            ingredients = listOf(
                listOf(ParsedIngredient(IngredientKind.ITEM, "minecraft:stick")),
                listOf(ParsedIngredient(IngredientKind.ITEM, "minecraft:stick"))
            ),
            width = 0,
            height = 0,
            source = "hand_craft",
            cookingTime = 0,
            experience = 0f
        )

        assertFalse(RecipeDatabase.canCraft(recipe, mapOf("stick" to 1)))
        assertTrue(RecipeDatabase.canCraft(recipe, mapOf("stick" to 2)))
    }

    @Test
    fun `canCraft consumes tag ingredients from available counts`() {
        val recipe = ParsedRecipe(
            id = "test_tag_count",
            kind = ParsedRecipeKind.SHAPELESS,
            resultId = "minecraft:test_result",
            resultCount = 1,
            ingredients = listOf(
                listOf(ParsedIngredient(IngredientKind.TAG, "minecraft:test_planks")),
                listOf(ParsedIngredient(IngredientKind.TAG, "minecraft:test_planks"))
            ),
            width = 0,
            height = 0,
            source = "hand_craft",
            cookingTime = 0,
            experience = 0f
        )

        val tagJson = """{ "values": ["minecraft:oak_planks", "minecraft:birch_planks"] }"""
        TagResolver.load(listOf("test_planks.json" to java.io.ByteArrayInputStream(tagJson.toByteArray(Charsets.UTF_8))))

        assertFalse(RecipeDatabase.canCraft(recipe, mapOf("oak_planks" to 1)))
        assertTrue(RecipeDatabase.canCraft(recipe, mapOf("oak_planks" to 1, "birch_planks" to 1)))
    }
}
