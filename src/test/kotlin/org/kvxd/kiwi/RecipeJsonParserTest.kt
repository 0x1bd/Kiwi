package org.kvxd.kiwi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.kvxd.kiwi.recipe.IngredientKind
import org.kvxd.kiwi.recipe.ParsedRecipeKind
import org.kvxd.kiwi.recipe.RecipeJsonParser

class RecipeJsonParserTest {

    private fun parse(json: String, name: String = "test.json") =
        RecipeJsonParser.parse(json.byteInputStream(), name)

    @Test
    fun `parses a shaped recipe and its grid`() {
        val recipe = requireNotNull(
            parse(
                """
                {
                  "type": "minecraft:crafting_shaped",
                  "pattern": ["XXX", " # ", " # "],
                  "key": { "X": "minecraft:cobblestone", "#": "minecraft:stick" },
                  "result": { "id": "minecraft:stone_pickaxe", "count": 1 }
                }
                """.trimIndent()
            )
        )

        assertEquals(ParsedRecipeKind.SHAPED, recipe.kind)
        assertEquals("minecraft:stone_pickaxe", recipe.resultId)
        assertEquals(3, recipe.width)
        assertEquals(3, recipe.height)
        assertEquals("crafting_table", recipe.source)
        assertEquals(9, recipe.ingredients.size)
        assertEquals(5, recipe.flatIngredients.size)
    }

    @Test
    fun `two by two shaped recipes are hand craftable`() {
        val recipe = requireNotNull(
            parse(
                """
                {
                  "type": "minecraft:crafting_shaped",
                  "pattern": ["#", "#"],
                  "key": { "#": "minecraft:oak_planks" },
                  "result": { "id": "minecraft:stick", "count": 4 }
                }
                """.trimIndent()
            )
        )

        assertEquals("hand_craft", recipe.source)
        assertEquals(4, recipe.resultCount)
    }

    @Test
    fun `parses tag ingredients`() {
        val recipe = requireNotNull(
            parse(
                """
                {
                  "type": "minecraft:crafting_shapeless",
                  "ingredients": ["#minecraft:logs"],
                  "result": { "id": "minecraft:oak_planks", "count": 4 }
                }
                """.trimIndent()
            )
        )

        val ingredient = recipe.flatIngredients.single()
        assertEquals(IngredientKind.TAG, ingredient.kind)
        assertEquals("minecraft:logs", ingredient.name)
        assertTrue(recipe.isShapeless)
    }

    @Test
    fun `parses smelting recipes`() {
        val recipe = requireNotNull(
            parse(
                """
                {
                  "type": "minecraft:smelting",
                  "ingredient": "minecraft:raw_iron",
                  "result": { "id": "minecraft:iron_ingot" },
                  "experience": 0.7,
                  "cookingtime": 200
                }
                """.trimIndent()
            )
        )

        assertEquals(ParsedRecipeKind.SMELTING, recipe.kind)
        assertTrue(recipe.isCooking)
        assertEquals(200, recipe.cookingTime)
        assertEquals(1, recipe.resultCount)
    }

    @Test
    fun `unsupported recipe types are skipped`() {
        assertEquals(null, parse("""{ "type": "minecraft:smithing_transform" }"""))
    }
}
