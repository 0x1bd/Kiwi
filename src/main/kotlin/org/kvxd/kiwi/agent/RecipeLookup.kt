package org.kvxd.kiwi.agent

import net.minecraft.core.registries.BuiltInRegistries
import org.kvxd.kiwi.harvest.BlockHarvestInfo
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.recipe.ParsedRecipe
import org.kvxd.kiwi.recipe.RecipeDatabase
import org.kvxd.kiwi.recipe.TagResolver
import org.kvxd.kiwi.util.ClientMessenger
import org.slf4j.LoggerFactory

enum class ItemSource {
    HAND_CRAFT,
    CRAFTING_TABLE,
    SMELTING,
    BLOCK_DROP
}

data class RecipeIngredient(
    val count: Int,
    val itemIds: List<String>,
    val displayName: String
) {
    val isTag: Boolean get() = itemIds.size > 1

    companion object {
        fun fromParsed(ingredient: org.kvxd.kiwi.recipe.ParsedIngredient): RecipeIngredient {
            val itemIds = when (ingredient.kind) {
                org.kvxd.kiwi.recipe.IngredientKind.TAG -> {
                    val resolved = TagResolver.getItems(ingredient.name)
                    if (resolved.isNotEmpty()) resolved.map { it.removePrefix("minecraft:") }
                    else listOf(ingredient.name.removePrefix("minecraft:"))
                }
                org.kvxd.kiwi.recipe.IngredientKind.ITEM -> listOf(ingredient.name.removePrefix("minecraft:"))
            }
            return RecipeIngredient(
                count = 1,
                itemIds = itemIds,
                displayName = ingredient.displayName
            )
        }
    }
}

data class Recipe(
    val result: () -> net.minecraft.world.item.Item,
    val resultCount: Int,
    val source: ItemSource,
    val ingredients: List<RecipeIngredient>,
    val resultId: String,
    val slots: List<RecipeIngredient?>,
    val width: Int = 0,
    val height: Int = 0,
    val parsedRecipe: ParsedRecipe? = null
)

object RecipeLookup {

    private val logger = LoggerFactory.getLogger("kiwi:RecipeLookup")

    @Volatile
    private var recipesLoaded = false

    private var _craftingRecipes: List<Recipe> = emptyList()
    private var _cookingRecipes: List<Recipe> = emptyList()
    private var _recipesByResult: Map<String, List<Recipe>> = emptyMap()
    private var _harvestByBlock: Map<String, BlockHarvest> = emptyMap()
    private var _harvestByDrop: Map<String, BlockHarvest> = emptyMap()
    private var _harvestSourcesByDrop: Map<String, List<BlockHarvest>> = emptyMap()

    val recipes: List<Recipe>
        get() {
            ensureLoaded()
            return _craftingRecipes
        }

    val cookingRecipes: List<Recipe>
        get() {
            ensureLoaded()
            return _cookingRecipes
        }

    val recipesByResult: Map<String, List<Recipe>>
        get() {
            ensureLoaded()
            return _recipesByResult
        }

    val harvestByBlock: Map<String, BlockHarvest>
        get() {
            ensureLoaded()
            return _harvestByBlock
        }

    val harvestByDrop: Map<String, BlockHarvest>
        get() {
            ensureLoaded()
            return _harvestByDrop
        }

    fun getRecipesFor(itemId: String): List<Recipe> = recipesByResult[itemId].orEmpty()

    fun getHarvestFor(blockId: String): BlockHarvest? {
        ensureLoaded()
        return _harvestByBlock[blockId.removePrefix("minecraft:")] ?: _harvestByBlock[blockId]
    }

    fun getHarvestByDrop(dropId: String): BlockHarvest? {
        ensureLoaded()
        return _harvestByDrop[dropId.removePrefix("minecraft:")] ?: _harvestByDrop[dropId]
    }

    fun getHarvestSourcesForDrop(dropId: String): List<BlockHarvest> {
        ensureLoaded()
        val key = dropId.removePrefix("minecraft:")
        return _harvestSourcesByDrop[key].orEmpty()
    }

    fun findBlockAlternatives(dropId: String): List<String> {
        val sources = getHarvestSourcesForDrop(dropId)
        if (sources.isNotEmpty()) return sources.map { it.blockId }.distinct()
        return HarvestDatabase.findBlockAlternatives(dropId)
    }

    fun reloadRecipes() {
        recipesLoaded = false
        ensureLoaded()
    }

    private fun ensureLoaded() {
        if (recipesLoaded) return

        if (!RecipeDatabase.isLoaded) {
            ClientMessenger.debug("RecipeDatabase not yet loaded")
            return
        }

        buildFromDatabase()
        recipesLoaded = true
    }

    private fun buildFromDatabase() {
        val allRecipes = RecipeDatabase.allRecipes

        val crafting = mutableListOf<Recipe>()
        val cooking = mutableListOf<Recipe>()
        val resultMap = mutableMapOf<String, MutableList<Recipe>>()

        val seen = mutableSetOf<String>()

        for (parsed in allRecipes) {
            if (parsed.isCrafting) {
                val dedupKey = buildDedupKey(parsed)
                if (dedupKey in seen) continue
                seen.add(dedupKey)

                val source = when (parsed.source) {
                    "hand_craft" -> ItemSource.HAND_CRAFT
                    "crafting_table" -> ItemSource.CRAFTING_TABLE
                    else -> ItemSource.HAND_CRAFT
                }

                val item = findItem(parsed.resultId)
                if (item == null) {
                    continue
                }

                val ingredients = parsed.ingredients.mapNotNull { slot ->
                    slot.firstOrNull()?.let { RecipeIngredient.fromParsed(it) }
                }

                val slots = parsed.gridSlots.map { it?.let { RecipeIngredient.fromParsed(it) } }

                val merged = mergeIngredients(ingredients.filterNotNull())

                val recipe = Recipe(
                    result = { item },
                    resultCount = parsed.resultCount,
                    source = source,
                    ingredients = merged,
                    resultId = parsed.resultId.removePrefix("minecraft:"),
                    slots = slots,
                    width = parsed.width,
                    height = parsed.height,
                    parsedRecipe = parsed
                )

                crafting.add(recipe)
                resultMap.getOrPut(recipe.resultId) { mutableListOf() }.add(recipe)
            }

            if (parsed.isCooking) {
                val source = when (parsed.kind) {
                    org.kvxd.kiwi.recipe.ParsedRecipeKind.SMELTING -> ItemSource.SMELTING
                    org.kvxd.kiwi.recipe.ParsedRecipeKind.BLASTING -> ItemSource.SMELTING
                    org.kvxd.kiwi.recipe.ParsedRecipeKind.SMOKING -> ItemSource.SMELTING
                    org.kvxd.kiwi.recipe.ParsedRecipeKind.CAMPFIRE_COOKING -> ItemSource.SMELTING
                    else -> ItemSource.SMELTING
                }

                val item = findItem(parsed.resultId)
                if (item == null) continue

                val ingredients = parsed.ingredients.mapNotNull { slot ->
                    slot.firstOrNull()?.let { RecipeIngredient.fromParsed(it) }
                }

                val recipe = Recipe(
                    result = { item },
                    resultCount = parsed.resultCount,
                    source = source,
                    ingredients = ingredients,
                    resultId = parsed.resultId.removePrefix("minecraft:"),
                    slots = ingredients,
                    width = 0,
                    height = 0,
                    parsedRecipe = parsed
                )

                cooking.add(recipe)
            }
        }

        _craftingRecipes = crafting
        _cookingRecipes = cooking
        _recipesByResult = resultMap

        buildHarvestMaps()

        logger.info("Loaded ${_craftingRecipes.size} crafting + ${_cookingRecipes.size} cooking recipes, ${_harvestByBlock.size} harvests")
    }

    private fun buildHarvestMaps() {
        val harvests = mutableMapOf<String, BlockHarvest>()
        val sourcesByDrop = mutableMapOf<String, MutableList<BlockHarvest>>()

        for (info in HarvestDatabase.allBlocks) {
            val block = findBlock(info.blockId) ?: continue
            val drop = findItem(info.primaryDropId) ?: continue
            val tool = info.bestToolItemId()?.let { findItem(it) }

            val bh = BlockHarvest(
                block = { block },
                drops = { drop },
                dropCount = info.dropCount,
                preferredTool = { tool },
                blockId = info.blockId,
                dropId = info.primaryDropId
            )

            if (info.blockId !in harvests) {
                harvests[info.blockId] = bh
            }

            sourcesByDrop.getOrPut(info.primaryDropId) { mutableListOf() }.add(bh)
        }

        _harvestByBlock = harvests
        _harvestSourcesByDrop = sourcesByDrop.mapValues { (_, sources) ->
            sources
                .distinctBy { it.blockId }
                .sortedWith(
                    compareBy<BlockHarvest> { it.isSelfDrop }
                        .thenBy { it.blockId }
                )
        }
        _harvestByDrop = _harvestSourcesByDrop.mapValues { (_, sources) -> sources.first() }
    }

    private fun findItem(id: String): net.minecraft.world.item.Item? {
        val fullId = if (id.contains(":")) id else "minecraft:$id"
        return BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(fullId)).orElse(null)
    }

    private fun findBlock(id: String): net.minecraft.world.level.block.Block? {
        val fullId = if (id.contains(":")) id else "minecraft:$id"
        return BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse(fullId)).orElse(null)
    }

    private fun mergeIngredients(rawIngredients: List<RecipeIngredient>): List<RecipeIngredient> {
        val grouped = mutableMapOf<String, RecipeIngredient>()
        for (ingredient in rawIngredients) {
            val key = ingredient.itemIds.sorted().joinToString(",")
            val existing = grouped[key]
            if (existing != null) {
                grouped[key] = existing.copy(count = existing.count + 1)
            } else {
                grouped[key] = ingredient
            }
        }
        return grouped.values.toList()
    }

    private fun buildDedupKey(parsed: ParsedRecipe): String {
        val ingredientStr = parsed.ingredients.flatten()
            .sortedBy { it.name }
            .joinToString(",") { "${it.name}:1" }
        return "${parsed.resultId}|${parsed.resultCount}|$ingredientStr"
    }
}

data class BlockHarvest(
    val block: () -> net.minecraft.world.level.block.Block,
    val drops: () -> net.minecraft.world.item.Item,
    val dropCount: IntRange,
    val preferredTool: () -> net.minecraft.world.item.Item?,
    val blockId: String,
    val dropId: String
) {
    val isSelfDrop: Boolean get() = blockId == dropId
}
