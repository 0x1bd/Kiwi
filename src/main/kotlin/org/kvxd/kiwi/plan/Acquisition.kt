package org.kvxd.kiwi.plan

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.world.item.ItemStack
import org.kvxd.kiwi.harvest.HarvestPlanner
import org.kvxd.kiwi.knowledge.CraftRecipe
import org.kvxd.kiwi.knowledge.CraftStation
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.knowledge.Ingredient
import org.kvxd.kiwi.knowledge.Knowledge
import org.kvxd.kiwi.knowledge.SmeltRecipe

sealed interface AcquisitionPlan {

    val cost: Double
    val reason: String

    data class Collect(override val cost: Double, override val reason: String) : AcquisitionPlan

    data class Harvest(
        val sourceBlocks: IntSet,
        override val cost: Double,
        override val reason: String
    ) : AcquisitionPlan

    data class Craft(
        val recipe: CraftRecipe,
        override val cost: Double,
        override val reason: String
    ) : AcquisitionPlan

    data class Smelt(
        val recipe: SmeltRecipe,
        override val cost: Double,
        override val reason: String
    ) : AcquisitionPlan

    class Need(
        val itemOptions: IntArray,
        val amount: Int,
        val label: String,
        override val cost: Double,
        override val reason: String
    ) : AcquisitionPlan

    data class Impossible(override val reason: String) : AcquisitionPlan {
        override val cost: Double get() = Double.POSITIVE_INFINITY
    }
}

class AcquisitionInputs(
    val counts: (Int) -> Int,
    val tools: List<ItemStack>,
    val nearbyDrops: (IntArray) -> Boolean,
    val harvestDistance: (IntSet) -> Double,
    val hasCraftingTable: Boolean,
    val hasFurnace: Boolean,
    val avoid: IntSet = IntOpenHashSet(),
    val blockedRecipes: Set<String> = emptySet(),
    val harvestDisabled: Boolean = false,
    val collectDisabled: Boolean = false
)

object Acquisition {

    private const val COLLECT_BONUS = -50.0
    private const val CRAFT_READY_BONUS = -20.0
    private const val TOOL_PRIORITY = -35.0
    private const val IMPLAUSIBLE_PENALTY = 1000.0
    private const val TRAVEL_WEIGHT = 0.25
    private const val PLAUSIBILITY_DEPTH = 3

    fun plan(wanted: IntArray, stillNeeded: Int, rawInputs: AcquisitionInputs): AcquisitionPlan {
        val inputs = Memo(rawInputs)
        val candidates = ArrayList<AcquisitionPlan>(8)

        if (!inputs.collectDisabled && inputs.nearbyDrops(wanted)) {
            candidates.add(AcquisitionPlan.Collect(COLLECT_BONUS, "drops are already on the ground"))
        }

        addHarvest(wanted, inputs, candidates)
        addCrafting(wanted, stillNeeded, inputs, candidates)
        addSmelting(wanted, stillNeeded, inputs, candidates)

        val best = candidates.minByOrNull { it.cost }
        if (best == null || best.cost.isInfinite()) {
            return AcquisitionPlan.Impossible("no way to obtain ${label(wanted)} from here")
        }
        return best
    }

    private fun addHarvest(wanted: IntArray, inputs: Memo, out: MutableList<AcquisitionPlan>) {
        val usable = HarvestPlanner.sourceBlocks(wanted, inputs.tools)
        if (usable.isNotEmpty() && !inputs.harvestDisabled) {
            val distance = inputs.harvestDistance(usable)
            if (distance.isFinite()) {
                val cost = wanted.minOf { Knowledge.acquisitionCost(it) } + distance * TRAVEL_WEIGHT
                out.add(
                    AcquisitionPlan.Harvest(
                        usable,
                        cost,
                        "harvestable blocks ${"%.0f".format(distance)} blocks away"
                    )
                )
                return
            }
        }

        val missingTools = HarvestPlanner.missingTools(wanted, inputs.tools)
        if (missingTools.isEmpty()) return

        val anySource = HarvestPlanner.allSourceBlocks(wanted)
        if (anySource.isEmpty() || !inputs.harvestDistance(anySource).isFinite()) return

        val cheapest = missingTools
            .map { Ids.item(it) }
            .filter { it >= 0 && it !in inputs.avoid && Knowledge.isObtainable(it) }
            .minByOrNull { Knowledge.acquisitionCost(it) } ?: return

        out.add(
            AcquisitionPlan.Need(
                itemOptions = intArrayOf(cheapest),
                amount = 1,
                label = Ids.itemName(cheapest),
                cost = Knowledge.acquisitionCost(cheapest) + TOOL_PRIORITY,
                reason = "need ${Ids.itemName(cheapest)} to harvest ${label(wanted)}"
            )
        )
    }

    private fun addCrafting(
        wanted: IntArray,
        stillNeeded: Int,
        inputs: Memo,
        out: MutableList<AcquisitionPlan>
    ) {
        for (item in wanted) {
            if (item in inputs.avoid) continue

            for (recipe in Knowledge.craftsFor(item)) {
                if (recipe.id in inputs.blockedRecipes) continue
                if (recipe.ingredients.any { ingredient -> ingredient.options.all { it in inputs.avoid } }) continue

                val batches = batchesFor(stillNeeded, recipe.resultCount)
                val missing = missingIngredients(recipe.ingredients, batches, inputs)

                if (missing.isEmpty()) {
                    if (needsTable(recipe, inputs)) {
                        addWorkstationNeed(CRAFTING_TABLE_ID, "crafting table", item, inputs, out, -10.0)
                        continue
                    }
                    out.add(
                        AcquisitionPlan.Craft(
                            recipe,
                            Knowledge.acquisitionCost(item) + CRAFT_READY_BONUS,
                            "all ingredients in inventory"
                        )
                    )
                    continue
                }

                val need = cheapestNeed(missing, inputs) ?: continue
                out.add(
                    AcquisitionPlan.Need(
                        itemOptions = need.options,
                        amount = need.count,
                        label = need.label,
                        cost = totalCost(missing, inputs) + 1.0,
                        reason = "need ${need.label} to craft ${Ids.itemName(item)}"
                    )
                )
            }
        }
    }

    private fun needsTable(recipe: CraftRecipe, inputs: Memo): Boolean =
        recipe.station == CraftStation.CRAFTING_TABLE &&
            !inputs.hasCraftingTable &&
            inputs.counts(CRAFTING_TABLE_ID) <= 0

    private fun addWorkstationNeed(
        itemId: Int,
        label: String,
        forItem: Int,
        inputs: Memo,
        out: MutableList<AcquisitionPlan>,
        bias: Double
    ) {
        if (itemId in inputs.avoid) return
        out.add(
            AcquisitionPlan.Need(
                itemOptions = intArrayOf(itemId),
                amount = 1,
                label = label,
                cost = Knowledge.acquisitionCost(itemId) + bias,
                reason = "need a $label for ${Ids.itemName(forItem)}"
            )
        )
    }

    private fun addSmelting(
        wanted: IntArray,
        stillNeeded: Int,
        inputs: Memo,
        out: MutableList<AcquisitionPlan>
    ) {
        for (item in wanted) {
            if (item in inputs.avoid) continue

            for (recipe in Knowledge.smeltsFor(item)) {
                if (recipe.id in inputs.blockedRecipes) continue
                if (recipe.input.options.all { it in inputs.avoid }) continue

                val batches = batchesFor(stillNeeded, recipe.resultCount)
                val missing = missingIngredients(arrayOf(recipe.input), batches, inputs)

                if (missing.isNotEmpty()) {
                    val need = cheapestNeed(missing, inputs) ?: continue
                    out.add(
                        AcquisitionPlan.Need(
                            need.options,
                            need.count,
                            need.label,
                            totalCost(missing, inputs) + 6.0,
                            "need ${need.label} to smelt ${Ids.itemName(item)}"
                        )
                    )
                    continue
                }

                if (!inputs.hasFurnace && inputs.counts(FURNACE_ID) <= 0) {
                    addWorkstationNeed(FURNACE_ID, "furnace", item, inputs, out, 0.0)
                    continue
                }

                if (FUEL_IDS.none { inputs.counts(it) > 0 }) {
                    val fuel = FUEL_IDS
                        .filter { it !in inputs.avoid && Knowledge.isObtainable(it) }
                        .minByOrNull { Knowledge.acquisitionCost(it) }
                    if (fuel != null) {
                        out.add(
                            AcquisitionPlan.Need(
                                intArrayOf(fuel),
                                1,
                                Ids.itemName(fuel),
                                Knowledge.acquisitionCost(fuel),
                                "need fuel to smelt ${Ids.itemName(item)}"
                            )
                        )
                    }
                    continue
                }

                out.add(AcquisitionPlan.Smelt(recipe, Knowledge.acquisitionCost(item), "ingredients and fuel ready"))
            }
        }
    }

    class MissingIngredient(val options: IntArray, val count: Int, val label: String)

    private fun missingIngredients(
        ingredients: Array<Ingredient>,
        batches: Int,
        inputs: Memo
    ): List<MissingIngredient> {
        val simulated = HashMap<Int, Int>()
        val missing = ArrayList<MissingIngredient>()

        for (ingredient in ingredients) {
            var remaining = ingredient.count * batches
            for (option in ingredient.options.sortedByDescending { inputs.counts(it) }) {
                val available = inputs.counts(option) - (simulated[option] ?: 0)
                if (available <= 0) continue
                val used = minOf(available, remaining)
                simulated[option] = (simulated[option] ?: 0) + used
                remaining -= used
                if (remaining <= 0) break
            }
            if (remaining > 0) {
                missing.add(MissingIngredient(ingredient.options, remaining, ingredient.label))
            }
        }
        return missing
    }

    private fun cheapestNeed(missing: List<MissingIngredient>, inputs: Memo): MissingIngredient? =
        missing
            .filter { need -> need.options.any { it !in inputs.avoid && Knowledge.isObtainable(it) } }
            .minByOrNull { need -> needCost(need, inputs) }

    private fun needCost(need: MissingIngredient, inputs: Memo): Double {
        val base = need.options
            .filter { it !in inputs.avoid }
            .minOfOrNull { Knowledge.acquisitionCost(it) } ?: Double.POSITIVE_INFINITY
        if (!base.isFinite()) return base
        return base * need.count + if (isPlausible(need.options, inputs)) 0.0 else IMPLAUSIBLE_PENALTY
    }

    private fun isPlausible(options: IntArray, inputs: Memo): Boolean = inputs.plausible(options, PLAUSIBILITY_DEPTH)

    private class Memo(private val delegate: AcquisitionInputs) {

        val tools get() = delegate.tools
        val avoid get() = delegate.avoid
        val blockedRecipes get() = delegate.blockedRecipes
        val harvestDisabled get() = delegate.harvestDisabled
        val collectDisabled get() = delegate.collectDisabled
        val hasCraftingTable get() = delegate.hasCraftingTable
        val hasFurnace get() = delegate.hasFurnace

        private val countCache = HashMap<Int, Int>()
        private val dropCache = HashMap<String, Boolean>()
        private val harvestCache = HashMap<String, Double>()
        private val plausibleCache = HashMap<String, Boolean>()

        fun counts(itemId: Int): Int = countCache.getOrPut(itemId) { delegate.counts(itemId) }

        fun nearbyDrops(options: IntArray): Boolean =
            dropCache.getOrPut(key(options)) { delegate.nearbyDrops(options) }

        fun harvestDistance(sources: IntSet): Double =
            harvestCache.getOrPut(sources.toIntArray().let { key(it) }) { delegate.harvestDistance(sources) }

        fun plausible(options: IntArray, depth: Int): Boolean =
            plausibleCache.getOrPut("${key(options)}@$depth") { compute(options, depth) }

        private fun compute(options: IntArray, depth: Int): Boolean {
            for (option in options) {
                if (option in avoid) continue
                if (counts(option) > 0) return true
            }
            if (nearbyDrops(options)) return true

            val usable = HarvestPlanner.sourceBlocks(options, tools)
            if (usable.isNotEmpty() && harvestDistance(usable).isFinite()) return true

            val anySource = HarvestPlanner.allSourceBlocks(options)
            if (anySource.isNotEmpty() && harvestDistance(anySource).isFinite()) {
                val tools = HarvestPlanner.missingTools(options, tools)
                if (tools.isEmpty()) return true
                if (tools.any { Ids.item(it).let { id -> id >= 0 && Knowledge.isObtainable(id) } }) return true
            }
            if (depth <= 0) return false

            for (option in options) {
                if (option in avoid) continue
                for (recipe in Knowledge.craftsFor(option)) {
                    if (recipe.ingredients.all { plausible(it.options, depth - 1) }) return true
                }
                for (recipe in Knowledge.smeltsFor(option)) {
                    if (plausible(recipe.input.options, depth - 1)) return true
                }
            }
            return false
        }

        private fun key(options: IntArray): String {
            if (options.size == 1) return options[0].toString()
            return options.sorted().joinToString(",")
        }
    }

    private fun totalCost(missing: List<MissingIngredient>, inputs: Memo): Double {
        var total = 0.0
        for (need in missing) {
            val cost = needCost(need, inputs)
            if (!cost.isFinite()) return Double.POSITIVE_INFINITY
            total += cost
        }
        return total
    }

    fun batchesFor(amount: Int, resultCount: Int): Int =
        maxOf(1, (amount + resultCount - 1) / resultCount)

    fun label(items: IntArray): String = items.joinToString("|") { Ids.itemName(it) }

    private val CRAFTING_TABLE_ID: Int by lazy { Ids.item("crafting_table") }
    private val FURNACE_ID: Int by lazy { Ids.item("furnace") }

    val FUEL_IDS: List<Int> by lazy {
        listOf("coal", "charcoal", "coal_block").map { Ids.item(it) }.filter { it >= 0 }
    }

    fun withAdded(base: IntSet, added: IntArray): IntSet {
        val result = IntOpenHashSet(base)
        for (item in added) result.add(item)
        return result
    }
}
