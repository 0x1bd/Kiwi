package org.kvxd.kiwi.recipe

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader

object RecipeJsonParser {

    private val logger = LoggerFactory.getLogger("kiwi:RecipeJsonParser")

    fun parseAll(inputs: List<Pair<String, InputStream>>): List<ParsedRecipe> {
        val recipes = mutableListOf<ParsedRecipe>()
        for ((filename, stream) in inputs) {
            try {
                val recipe = parse(stream, filename) ?: continue
                recipes.add(recipe)
            } catch (e: Exception) {
                logger.warn("Failed to parse recipe $filename: ${e.message}")
            } finally {
                try { stream.close() } catch (_: Exception) {}
            }
        }
        logger.info("Parsed ${recipes.size} recipes from ${inputs.size} files")
        return recipes
    }

    fun parse(input: InputStream, filename: String): ParsedRecipe? {
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val element = JsonParser.parseReader(reader)
        reader.close()

        if (!element.isJsonObject) {
            logger.warn("Top-level element is not a JSON object in $filename: $element")
            return null
        }

        val json = element.asJsonObject
        val type = json.get("type")?.asString ?: run {
            logger.warn("No type field in $filename")
            return null
        }

        return when (type) {
            "minecraft:crafting_shaped" -> parseShaped(json, filename)
            "minecraft:crafting_shapeless" -> parseShapeless(json, filename)
            "minecraft:smelting" -> parseCooking(json, filename, ParsedRecipeKind.SMELTING)
            "minecraft:blasting" -> parseCooking(json, filename, ParsedRecipeKind.BLASTING)
            "minecraft:smoking" -> parseCooking(json, filename, ParsedRecipeKind.SMOKING)
            "minecraft:campfire_cooking" -> parseCooking(json, filename, ParsedRecipeKind.CAMPFIRE_COOKING)
            "minecraft:stonecutting" -> parseStonecutting(json, filename)
            else -> {
                logger.debug("Skipping unsupported recipe type '$type' in $filename")
                null
            }
        }
    }

    private fun parseShaped(json: JsonObject, filename: String): ParsedRecipe? {
        val result = parseResult(json) ?: return null
        val pattern = json.getAsJsonArray("pattern") ?: run {
            logger.warn("No pattern in shaped recipe $filename")
            return null
        }
        val keyObj = json.getAsJsonObject("key") ?: run {
            logger.warn("No key in shaped recipe $filename")
            return null
        }

        val rows = pattern.map { it.asString }
        val height = rows.size
        val width = if (height > 0) rows[0].length else 0

        val ingredients: List<List<ParsedIngredient>> = buildList {
            for (row in rows) {
                for (ch in row.toCharArray()) {
                    val keyElement = keyObj.get(ch.toString())
                    if (keyElement != null) {
                        add(parseIngredient(keyElement))
                    } else {
                        add(emptyList())
                    }
                }
            }
        }

        val source = if (width <= 2 && height <= 2) "hand_craft" else "crafting_table"
        val recipeId = filename.removeSuffix(".json")

        return ParsedRecipe(
            id = recipeId,
            kind = ParsedRecipeKind.SHAPED,
            resultId = result.first,
            resultCount = result.second,
            ingredients = ingredients,
            width = width,
            height = height,
            source = source,
            cookingTime = 0,
            experience = 0f
        )
    }

    private fun parseShapeless(json: JsonObject, filename: String): ParsedRecipe? {
        val result = parseResult(json) ?: return null
        val ingredientsJson = json.getAsJsonArray("ingredients") ?: run {
            logger.warn("No ingredients in shapeless recipe $filename")
            return null
        }

        val ingredients: List<List<ParsedIngredient>> = ingredientsJson.map { elem ->
            parseIngredient(elem)
        }

        val source = if (ingredients.size <= 4) "hand_craft" else "crafting_table"
        val recipeId = filename.removeSuffix(".json")

        return ParsedRecipe(
            id = recipeId,
            kind = ParsedRecipeKind.SHAPELESS,
            resultId = result.first,
            resultCount = result.second,
            ingredients = ingredients,
            width = 0,
            height = 0,
            source = source,
            cookingTime = 0,
            experience = 0f
        )
    }

    private fun parseCooking(json: JsonObject, filename: String, kind: ParsedRecipeKind): ParsedRecipe? {
        val result = parseResult(json) ?: return null
        val ingredientJson = json.get("ingredient") ?: run {
            logger.warn("No ingredient in cooking recipe $filename")
            return null
        }

        val ingredients = listOf(parseIngredient(ingredientJson))
        val cookingTime = json.get("cookingtime")?.asInt ?: 200
        val experience = json.get("experience")?.asFloat ?: 0f

        val source = when (kind) {
            ParsedRecipeKind.SMELTING -> "furnace"
            ParsedRecipeKind.BLASTING -> "blast_furnace"
            ParsedRecipeKind.SMOKING -> "smoker"
            ParsedRecipeKind.CAMPFIRE_COOKING -> "campfire"
            else -> "furnace"
        }

        val recipeId = filename.removeSuffix(".json")

        return ParsedRecipe(
            id = recipeId,
            kind = kind,
            resultId = result.first,
            resultCount = result.second,
            ingredients = ingredients,
            width = 0,
            height = 0,
            source = source,
            cookingTime = cookingTime,
            experience = experience
        )
    }

    private fun parseStonecutting(json: JsonObject, filename: String): ParsedRecipe? {
        val result = parseResult(json) ?: return null
        val ingredientJson = json.get("ingredient") ?: run {
            logger.warn("No ingredient in stonecutting recipe $filename")
            return null
        }

        val ingredients = listOf(parseIngredient(ingredientJson))
        val recipeId = filename.removeSuffix(".json")

        return ParsedRecipe(
            id = recipeId,
            kind = ParsedRecipeKind.SHAPELESS,
            resultId = result.first,
            resultCount = result.second,
            ingredients = ingredients,
            width = 0,
            height = 0,
            source = "stonecutter",
            cookingTime = 0,
            experience = 0f
        )
    }

    private fun parseIngredient(element: JsonElement): List<ParsedIngredient> {
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { parseSingleIngredient(it) }
        }
        return parseSingleIngredient(element)?.let { listOf(it) } ?: emptyList()
    }

    private fun parseSingleIngredient(element: JsonElement): ParsedIngredient? {
        if (element.isJsonPrimitive) {
            val str = element.asString
            if (str.startsWith("#")) {
                return ParsedIngredient(kind = IngredientKind.TAG, name = str.removePrefix("#"))
            }
            return ParsedIngredient(kind = IngredientKind.ITEM, name = str)
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val item = obj.get("item")?.asString
            if (item != null) {
                return ParsedIngredient(kind = IngredientKind.ITEM, name = item)
            }
            val tag = obj.get("tag")?.asString
            if (tag != null) {
                return ParsedIngredient(kind = IngredientKind.TAG, name = tag)
            }
        }

        return null
    }

    private fun parseResult(json: JsonObject): Pair<String, Int>? {
        val result = json.get("result") ?: return null

        if (result.isJsonPrimitive) {
            return result.asString to 1
        }

        if (!result.isJsonObject) return null
        val resultObj = result.asJsonObject
        val id = resultObj.get("id")?.asString ?: return null
        val count = resultObj.get("count")?.asInt ?: 1
        return id to count
    }
}
