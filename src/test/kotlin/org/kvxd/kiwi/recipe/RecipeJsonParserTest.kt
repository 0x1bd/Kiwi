package org.kvxd.kiwi.recipe

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class RecipeJsonParserTest {

    private fun recipeStream(json: String): InputStream =
        ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

    @Test
    fun `parses shaped recipe`() {
        val recipe = RecipeJsonParser.parse(recipeStream(WOODEN_PICKAXE), "wooden_pickaxe.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SHAPED, recipe!!.kind)
        assertEquals("wooden_pickaxe", recipe.resultId.removePrefix("minecraft:"))
        assertEquals(1, recipe.resultCount)
        assertEquals(3, recipe.width)
        assertEquals(3, recipe.height)
        assertEquals("crafting_table", recipe.source)
    }

    @Test
    fun `parses shapeless recipe`() {
        val recipe = RecipeJsonParser.parse(recipeStream(FLINT_AND_STEEL), "flint_and_steel.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SHAPELESS, recipe!!.kind)
        assertEquals("flint_and_steel", recipe.resultId.removePrefix("minecraft:"))
        assertEquals("hand_craft", recipe.source)
    }

    @Test
    fun `parses smelting recipe`() {
        val recipe = RecipeJsonParser.parse(recipeStream(COAL_FROM_SMELTING), "coal_from_smelting_coal_ore.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SMELTING, recipe!!.kind)
        assertEquals("coal", recipe.resultId.removePrefix("minecraft:"))
        assertEquals(200, recipe.cookingTime)
        assertEquals(0.1f, recipe.experience)
        assertEquals(1, recipe.resultCount)
    }

    @Test
    fun `parses blasting recipe`() {
        val recipe = RecipeJsonParser.parse(recipeStream(COAL_FROM_BLASTING), "coal_from_blasting_coal_ore.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.BLASTING, recipe!!.kind)
    }

    @Test
    fun `parses smoking recipe`() {
        val recipe = RecipeJsonParser.parse(recipeStream(BAKED_POTATO_FROM_SMOKING), "baked_potato_from_smoking.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SMOKING, recipe!!.kind)
        assertEquals("baked_potato", recipe.resultId.removePrefix("minecraft:"))
    }

    @Test
    fun `parses tag ingredient`() {
        val recipe = RecipeJsonParser.parse(recipeStream(TAG_INGREDIENT_TEST), "test.json")
        assertNotNull(recipe)
        val tagIngredient = recipe!!.flatIngredients.find { it.kind == IngredientKind.TAG }
        assertNotNull(tagIngredient)
        assertEquals("minecraft:wooden_tool_materials", tagIngredient!!.name)
    }

    @Test
    fun `parses result with count`() {
        val recipe = RecipeJsonParser.parse(recipeStream(CRAFTING_TABLE), "crafting_table.json")
        assertNotNull(recipe)
        assertEquals(1, recipe!!.resultCount)
    }

    @Test
    fun `stonecutting recipe parses as shapeless`() {
        val recipe = RecipeJsonParser.parse(recipeStream(STONE_SLAB_FROM_STONECUTTING), "stone_slab_from_stone_stonecutting.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SHAPELESS, recipe!!.kind)
        assertEquals(2, recipe.resultCount)
        assertEquals("stonecutter", recipe.source)
    }

    @Test
    fun `classifies 2x2 shaped recipe as hand_craft`() {
        val recipe = RecipeJsonParser.parse(recipeStream(CRAFTING_TABLE_2X2), "crafting_table.json")
        assertNotNull(recipe)
        assertEquals("hand_craft", recipe!!.source)
    }

    @Test
    fun `classifies 3x3 shaped recipe as crafting_table`() {
        val recipe = RecipeJsonParser.parse(recipeStream(CRAFTING_TABLE_3X3), "wooden_pickaxe.json")
        assertNotNull(recipe)
        assertEquals("crafting_table", recipe!!.source)
    }

    @Test
    fun `isCrafting and isCooking convenience properties`() {
        val shaped = RecipeJsonParser.parse(recipeStream(STONE_BUTTON), "stone_button.json")!!
        val smelting = RecipeJsonParser.parse(recipeStream(IRON_INGOT_FROM_SMELTING), "iron_ingot.json")!!

        assertTrue(shaped.isCrafting)
        assertFalse(shaped.isCooking)
        assertTrue(smelting.isCooking)
        assertFalse(smelting.isCrafting)
    }

    @Test
    fun `parses stonecutting recipe as shapeless`() {
        val recipe = RecipeJsonParser.parse(recipeStream(STONE_SLAB_FROM_STONECUTTING), "stone_slab.json")
        assertNotNull(recipe)
        assertEquals(ParsedRecipeKind.SHAPELESS, recipe!!.kind)
        assertEquals(2, recipe.resultCount)
        assertEquals("stonecutter", recipe.source)
    }
}