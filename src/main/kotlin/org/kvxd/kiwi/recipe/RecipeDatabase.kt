package org.kvxd.kiwi.recipe

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

        val recipeInputs = collectResources("data/kiwi/recipes/vanilla", ".json")
        val tagInputs = collectResources("data/kiwi/tags/vanilla/items", ".json")

        if (tagInputs.isNotEmpty()) {
            TagResolver.load(tagInputs)
        } else {
            logger.warn("No item tag JSONs found - tag-based ingredients won't be resolved")
        }

        if (recipeInputs.isEmpty()) {
            logger.warn("No recipe JSONs found in data/kiwi/recipes/vanilla - run ./gradlew runDatagen first")
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

    private fun collectResources(basePath: String, extension: String): List<Pair<String, InputStream>> {
        val result = mutableListOf<Pair<String, InputStream>>()
        val classLoader = RecipeDatabase::class.java.classLoader
        val urls = classLoader.getResources(basePath).toList()

        for (url in urls) {
            when (url.protocol) {
                "file" -> {
                    val dir = try { Paths.get(url.toURI()) } catch (_: Exception) { continue }
                    if (Files.isDirectory(dir)) {
                        Files.list(dir).use { stream ->
                            stream.filter { it.fileName.toString().endsWith(extension) }
                                .forEach { path ->
                                    val name = path.fileName.toString()
                                    result.add(name to Files.newInputStream(path))
                                }
                        }
                    }
                }
                "jar" -> {
                    try {
                        val connection = url.openConnection() as? JarURLConnection ?: continue
                        val jarFile = connection.jarFile
                        val entries = jarFile.entries()
                        val prefix = if (basePath.endsWith("/")) basePath else "$basePath/"
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val entryName = entry.name
                            if (!entry.isDirectory && entryName.startsWith(prefix) && entryName.endsWith(extension)) {
                                val name = entryName.substringAfterLast("/")
                                result.add(name to jarFile.getInputStream(entry))
                            }
                        }
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
        return result
    }
}
