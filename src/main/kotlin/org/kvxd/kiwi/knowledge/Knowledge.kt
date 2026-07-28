package org.kvxd.kiwi.knowledge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.data.VanillaDataFiles
import org.kvxd.kiwi.recipe.IngredientKind
import org.kvxd.kiwi.recipe.ParsedIngredient
import org.kvxd.kiwi.recipe.ParsedRecipe
import org.kvxd.kiwi.recipe.ParsedRecipeKind
import org.kvxd.kiwi.recipe.RecipeJsonParser
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

object Knowledge {

    private val logger = LoggerFactory.getLogger("kiwi:Knowledge")

    @Volatile
    var isLoaded: Boolean = false
    private set

    var itemTags: TagTable = TagTable.EMPTY
    private set

    var blockTags: TagTable = TagTable.EMPTY
    private set

    private var craftRecipes: List<CraftRecipe> = emptyList()
    private var smeltRecipes: List<SmeltRecipe> = emptyList()

    private var craftByResult: Array<List<CraftRecipe>> = emptyArray()
    private var smeltByResult: Array<List<SmeltRecipe>> = emptyArray()
    private var harvestByBlock: Array<BlockHarvest?> = emptyArray()
    private var harvestByDrop: Array<List<BlockHarvest>> = emptyArray()
    private var costs: DoubleArray = DoubleArray(0)

    val allCraftRecipes: List<CraftRecipe> get() = craftRecipes
    val allSmeltRecipes: List<SmeltRecipe> get() = smeltRecipes

    @Synchronized
    fun load() {
        val started = System.nanoTime()

        itemTags = TagTable.parse(VanillaDataFiles.jsonInputs("tags/items"))
        blockTags = TagTable.parse(VanillaDataFiles.jsonInputs("tags/blocks"))

        val parsed = RecipeJsonParser.parseAll(VanillaDataFiles.jsonInputs("recipes"))
        buildRecipeIndices(parsed)
        buildHarvestIndices()
        buildCosts()

        isLoaded = true
        val ms = (System.nanoTime() - started) / 1_000_000.0
        logger.info(
            "Kiwi knowledge loaded in {}ms: {} craft, {} smelt, {} harvestable blocks, {} item tags",
            "%.1f".format(ms), craftRecipes.size, smeltRecipes.size, harvestByBlock.count { it != null }, itemTags.size
        )
    }

    fun reset() {
        isLoaded = false
        craftRecipes = emptyList()
        smeltRecipes = emptyList()
        craftByResult = emptyArray()
        smeltByResult = emptyArray()
        harvestByBlock = emptyArray()
        harvestByDrop = emptyArray()
        costs = DoubleArray(0)
    }

    fun craftsFor(itemId: Int): List<CraftRecipe> = craftByResult.getOrNull(itemId) ?: emptyList()

    fun smeltsFor(itemId: Int): List<SmeltRecipe> = smeltByResult.getOrNull(itemId) ?: emptyList()

    fun harvestOf(block: Block): BlockHarvest? = harvestByBlock.getOrNull(Ids.block(block))

    fun harvestOf(blockId: Int): BlockHarvest? = harvestByBlock.getOrNull(blockId)

    fun sourcesOf(itemId: Int): List<BlockHarvest> = harvestByDrop.getOrNull(itemId) ?: emptyList()

    fun acquisitionCost(itemId: Int): Double =
        if (itemId < 0 || itemId >= costs.size) Double.POSITIVE_INFINITY else costs[itemId]

    fun isObtainable(itemId: Int): Boolean = acquisitionCost(itemId).isFinite()

    fun tagItems(tagId: String): IntArray {
        val members = itemTags.members(tagId)
        if (members.isEmpty()) return IntArray(0)
        val ids = ArrayList<Int>(members.size)
        for (member in members) {
            val id = Ids.item(member)
            if (id != NO_ID) ids.add(id)
        }
        return ids.toIntArray()
    }

    private fun buildRecipeIndices(parsed: List<ParsedRecipe>) {
        val itemCount = Ids.itemCount
        val crafts = ArrayList<CraftRecipe>(parsed.size)
        val smelts = ArrayList<SmeltRecipe>(parsed.size / 4 + 8)
        val seen = HashSet<String>(parsed.size * 2)

        for (recipe in parsed) {
            val result = Ids.item(recipe.resultId)
            if (result == NO_ID) continue

            if (recipe.isCrafting) {
                if (recipe.source in UNSUPPORTED_STATIONS) continue
                val ingredients = mergedIngredients(recipe) ?: continue
                if (ingredients.isEmpty()) continue

                val key = dedupKey(result, recipe.resultCount, ingredients)
                if (!seen.add(key)) continue

                crafts.add(
                    CraftRecipe(
                        id = recipe.id,
                        result = result,
                        resultCount = recipe.resultCount.coerceAtLeast(1),
                        ingredients = ingredients,
                        station = if (recipe.source == "hand_craft") CraftStation.HAND else CraftStation.CRAFTING_TABLE,
                        width = recipe.width,
                        height = recipe.height,
                        shapedSlots = shapedSlots(recipe)
                    )
                )
            } else if (recipe.isCooking && recipe.kind != ParsedRecipeKind.CAMPFIRE_COOKING) {
                val input = ingredientOf(recipe.ingredients.firstOrNull().orEmpty()) ?: continue
                smelts.add(
                    SmeltRecipe(
                        id = recipe.id,
                        result = result,
                        resultCount = recipe.resultCount.coerceAtLeast(1),
                        input = input,
                        cookingTime = recipe.cookingTime
                    )
                )
            }
        }

        craftRecipes = crafts
        smeltRecipes = smelts

        val craftBuckets = Array(itemCount) { mutableListOf<CraftRecipe>() }
        for (recipe in crafts) craftBuckets[recipe.result].add(recipe)
        for (bucket in craftBuckets) {
            bucket.sortWith(compareBy({ it.station.ordinal }, { it.ingredients.size }))
        }
        craftByResult = Array(itemCount) { craftBuckets[it] }

        val smeltBuckets = Array(itemCount) { mutableListOf<SmeltRecipe>() }
        for (recipe in smelts) smeltBuckets[recipe.result].add(recipe)
        smeltByResult = Array(itemCount) { smeltBuckets[it] }
    }

    private fun shapedSlots(recipe: ParsedRecipe): Array<Ingredient?> {
        if (!recipe.isShaped) return emptyArray()
        return Array(recipe.ingredients.size) { index -> ingredientOf(recipe.ingredients[index]) }
    }

    private fun mergedIngredients(recipe: ParsedRecipe): Array<Ingredient>? {
        val grouped = LinkedHashMap<String, Ingredient>()
        for (slot in recipe.ingredients) {
            if (slot.isEmpty()) continue
            val ingredient = ingredientOf(slot) ?: return null
            val key = ingredient.options.joinToString(",")
            val existing = grouped[key]
            grouped[key] = if (existing == null) {
                ingredient
            } else {
                Ingredient(existing.count + 1, existing.options, existing.label)
            }
        }
        return grouped.values.toTypedArray()
    }

    private fun ingredientOf(slot: List<ParsedIngredient>): Ingredient? {
        if (slot.isEmpty()) return null

        val options = LinkedHashSet<Int>()
        val labels = LinkedHashSet<String>()

        for (entry in slot) {
            when (entry.kind) {
                IngredientKind.ITEM -> {
                    val id = Ids.item(entry.name)
                    if (id != NO_ID) options.add(id)
                    labels.add(entry.displayName)
                }

                IngredientKind.TAG -> {
                    val resolved = tagItems(entry.name)
                    if (resolved.isEmpty()) {
                        val id = Ids.item(entry.name)
                        if (id != NO_ID) options.add(id)
                    } else {
                        for (id in resolved) options.add(id)
                    }
                    labels.add("#${entry.displayName}")
                }
            }
        }

        if (options.isEmpty()) return null
        return Ingredient(1, options.toIntArray(), labels.joinToString("|"))
    }

    private fun dedupKey(result: Int, count: Int, ingredients: Array<Ingredient>): String {
        val parts = ingredients.map { "${it.count}:${it.options.sorted().joinToString(",")}" }.sorted()
        return "$result|$count|${parts.joinToString(";")}"
    }

    private fun buildHarvestIndices() {
        val blockCount = Ids.blockCount
        val itemCount = Ids.itemCount
        val lootDrops = parseLootTables()

        val byBlock = arrayOfNulls<BlockHarvest>(blockCount)
        val byDrop = Array(itemCount) { mutableListOf<BlockHarvest>() }

        for (block in BuiltInRegistries.BLOCK) {
            val blockId = Ids.block(block)
            val name = BuiltInRegistries.BLOCK.getKey(block).toString()

            val loot = lootDrops[name]
            val dropName = loot?.item ?: BuiltInRegistries.BLOCK.getKey(block).path
            val drop = Ids.item(dropName)
            if (drop == NO_ID) continue

            val tool = detectTool(name)
            val tier = detectTier(name, block)
            val requiresTool = runCatching { block.defaultBlockState().requiresCorrectToolForDrops() }
                .getOrDefault(tier != ToolTier.NONE)

            val harvest = BlockHarvest(
                block = blockId,
                drop = drop,
                minCount = loot?.min ?: 1,
                maxCount = loot?.max ?: 1,
                tool = tool,
                tier = tier,
                requiresCorrectTool = requiresTool
            )

            byBlock[blockId] = harvest
            byDrop[drop].add(harvest)
        }

        harvestByBlock = byBlock
        harvestByDrop = Array(itemCount) { index ->
            byDrop[index].sortedWith(compareBy({ it.isSelfDrop }, { it.tier.level }, { Ids.blockName(it.block) }))
        }
    }

    private fun detectTool(blockName: String): ToolKind = when {
        blockTags.contains("minecraft:mineable/pickaxe", blockName) -> ToolKind.PICKAXE
        blockTags.contains("minecraft:mineable/axe", blockName) -> ToolKind.AXE
        blockTags.contains("minecraft:mineable/shovel", blockName) -> ToolKind.SHOVEL
        blockTags.contains("minecraft:mineable/hoe", blockName) -> ToolKind.HOE
        blockTags.contains("minecraft:sword_efficient", blockName) -> ToolKind.SWORD
        else -> ToolKind.ANY
    }

    private fun detectTier(blockName: String, block: Block): ToolTier = when {
        blockTags.contains("minecraft:needs_diamond_tool", blockName) -> ToolTier.DIAMOND
        blockTags.contains("minecraft:needs_iron_tool", blockName) -> ToolTier.IRON
        blockTags.contains("minecraft:needs_stone_tool", blockName) -> ToolTier.STONE
        else -> when {
            block.defaultDestroyTime() >= 50f -> ToolTier.NETHERITE
            block.defaultDestroyTime() >= 30f -> ToolTier.DIAMOND
            block.defaultDestroyTime() >= 5f -> ToolTier.IRON
            block.defaultDestroyTime() >= 3f -> ToolTier.STONE
            else -> ToolTier.WOOD
        }
    }

    private class LootDrop(val item: String, val min: Int, val max: Int)

    private fun parseLootTables(): Map<String, LootDrop> {
        val drops = HashMap<String, LootDrop>(1024)

        for ((filename, stream) in VanillaDataFiles.jsonInputs("loot_tables/blocks")) {
            try {
                val json = InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it) }
                if (!json.isJsonObject) continue

                val pools = json.asJsonObject.getAsJsonArray("pools") ?: continue
                if (pools.isEmpty) continue
                val entries = pools[0].asJsonObject?.getAsJsonArray("entries") ?: continue
                if (entries.isEmpty) continue

                val entry = entries[0].asJsonObject ?: continue
                val resolved = resolveLootEntry(entry) ?: continue

                drops["minecraft:${filename.removeSuffix(".json")}"] = resolved
            } catch (_: Exception) {
            } finally {
                runCatching { stream.close() }
            }
        }
        return drops
    }

    private fun resolveLootEntry(entry: JsonObject): LootDrop? {
        return when (entry.get("type")?.asString) {
            "minecraft:item" -> {
                val name = entry.get("name")?.asString ?: return null
                val (min, max) = countRange(entry)
                LootDrop(name, min, max)
            }

            "minecraft:alternatives" -> {
                val children = entry.getAsJsonArray("children") ?: return null
                if (children.isEmpty) return null
                resolveLootEntry(children[children.size() - 1].asJsonObject ?: return null)
            }

            else -> null
        }
    }

    private fun countRange(entry: JsonObject): Pair<Int, Int> {
        val functions = entry.getAsJsonArray("functions") ?: return 1 to 1
        for (element in functions) {
            val function = element.asJsonObject ?: continue
            if (function.get("function")?.asString != "minecraft:set_count") continue
            val count = function.get("count") ?: continue
            if (count.isJsonPrimitive) {
                val value = count.asInt
                return value to value
            }
            if (count.isJsonObject) {
                val obj = count.asJsonObject
                val min = obj.get("min")?.asDouble
                val max = obj.get("max")?.asDouble
                if (min != null && max != null) return min.toInt() to max.toInt()
                val value = obj.get("value")
                if (value != null && value.isJsonPrimitive) {
                    val v = value.asInt
                    return v to v
                }
            }
        }
        return 1 to 1
    }

    private val UNSUPPORTED_STATIONS = setOf("stonecutter")

    private const val MINE_BASE = 4.0
    private const val TIER_PENALTY = 2.5
    private const val CRAFT_OVERHEAD = 1.0
    private const val SMELT_OVERHEAD = 8.0
    private const val RELAXATION_PASSES = 8

    private fun buildCosts() {
        val itemCount = Ids.itemCount
        val result = DoubleArray(itemCount) { Double.POSITIVE_INFINITY }

        for (harvest in harvestByBlock) {
            if (harvest == null) continue
            if (!occursNaturally(harvest)) continue
            val mineCost = MINE_BASE + harvest.tier.level * harvest.tier.level * TIER_PENALTY
            if (mineCost < result[harvest.drop]) result[harvest.drop] = mineCost
        }

        repeat(RELAXATION_PASSES) {
            var changed = false

            for (recipe in craftRecipes) {
                val cost = ingredientsCost(recipe.ingredients, result)
                if (!cost.isFinite()) continue
                val candidate = cost / recipe.resultCount + CRAFT_OVERHEAD
                if (candidate < result[recipe.result] - 1.0E-6) {
                    result[recipe.result] = candidate
                    changed = true
                }
            }

            for (recipe in smeltRecipes) {
                val cost = ingredientsCost(arrayOf(recipe.input), result)
                if (!cost.isFinite()) continue
                val candidate = cost / recipe.resultCount + SMELT_OVERHEAD
                if (candidate < result[recipe.result] - 1.0E-6) {
                    result[recipe.result] = candidate
                    changed = true
                }
            }

            if (!changed) return@repeat
        }

        costs = result
    }

    private fun occursNaturally(harvest: BlockHarvest): Boolean {
        val blockItem = Ids.item(Ids.blockName(harvest.block))
        if (blockItem == NO_ID) return true
        return craftByResult.getOrNull(blockItem).isNullOrEmpty()
    }

    private fun ingredientsCost(ingredients: Array<Ingredient>, costs: DoubleArray): Double {
        var total = 0.0
        for (ingredient in ingredients) {
            var best = Double.POSITIVE_INFINITY
            for (option in ingredient.options) {
                val cost = costs[option]
                if (cost < best) best = cost
            }
            if (!best.isFinite()) return Double.POSITIVE_INFINITY
            total += best * ingredient.count
        }
        return total
    }
}
