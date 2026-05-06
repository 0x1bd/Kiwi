package org.kvxd.kiwi.agent.planning

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.agent.ItemSource
import org.kvxd.kiwi.agent.Recipe
import org.kvxd.kiwi.agent.RecipeIngredient
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.agent.WorkstationIds
import org.kvxd.kiwi.agent.job.AgentRequest
import org.kvxd.kiwi.agent.job.GoalFrame
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.harvest.HarvestToolTier
import org.kvxd.kiwi.harvest.HarvestToolType
import org.kvxd.kiwi.recipe.ParsedRecipe
import org.kvxd.kiwi.recipe.RecipeGraph
import org.kvxd.kiwi.recipe.TagResolver
import org.kvxd.kiwi.util.coroutine.waitClientTicks
import org.kvxd.kiwi.util.registryPath
import kotlin.math.max

object PlanningEngine {

    private val recipeGraph = RecipeGraph()
    private const val MAX_RECIPE_SEARCH = 48

    fun initialize() {
        recipeGraph.build()
    }

    data class PlanRequest(
        val root: AgentRequest,
        val activeGoal: GoalFrame,
        val goals: List<GoalFrame>,
        val blockedCraftItems: Set<String>,
        val blockedMineItems: Set<String>,
        val inventoryCounts: Map<String, Int>,
        val playerPos: BlockPos,
        val environment: EnvironmentQuery
    )

    class EnvironmentQuery(
        val findNearestDrop: suspend (String) -> BlockPos?,
        val findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        val findNearestBlock: suspend (Block) -> BlockPos?,
        val findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    )

    sealed class PlanDecision {
        abstract val reason: String
        abstract val score: Double

        data class CollectDrop(
            val itemId: String,
            val acceptedItemIds: Set<String>,
            val nearestDrop: BlockPos?,
            override val reason: String = "nearby drop",
            override val score: Double = 0.0
        ) : PlanDecision()

        data class MineBlock(
            val block: Block,
            val targetPos: BlockPos,
            val dropId: String,
            val count: Int = 1,
            val acceptedItemIds: Set<String> = setOf(dropId),
            override val reason: String = "visible harvest source",
            override val score: Double = 0.0
        ) : PlanDecision() {
            val blockId: String get() = block.registryPath
        }

        data class CraftItem(
            val itemId: String,
            val recipe: Recipe,
            override val reason: String = "ingredients available",
            override val score: Double = 0.0
        ) : PlanDecision()

        data class SmeltItem(
            val itemId: String,
            val recipe: Recipe,
            override val reason: String = "ingredients and fuel available",
            override val score: Double = 0.0
        ) : PlanDecision()

        data class AcquireItem(
            val itemId: String,
            val acceptedItemIds: Set<String> = setOf(itemId),
            val displayName: String? = null,
            val amount: Int,
            override val reason: String,
            override val score: Double = 0.0
        ) : PlanDecision()

        data class NoPlan(
            override val reason: String = "no available source",
            override val score: Double = 0.0
        ) : PlanDecision()
    }

    suspend fun nextStep(plan: PlanRequest): PlanDecision {
        val activeGoal = plan.activeGoal
        val activeRemaining = activeGoal.remaining(plan.inventoryCounts)
        if (activeRemaining <= 0) return PlanDecision.NoPlan("goal already complete")

        val candidates = mutableListOf<ScoredDecision>()
        val ancestors = plan.goals.dropLast(1).flatMapTo(mutableSetOf()) { it.acceptedItemIds }
        val blockedItems = plan.blockedCraftItems + plan.blockedMineItems
        val dropCache = mutableMapOf<String, BlockPos?>()
        val dropSetCache = mutableMapOf<String, Pair<String, BlockPos>?>()
        val blockCache = mutableMapOf<String, Pair<Block, BlockPos>?>()
        val cachedFindNearestDrop: suspend (String) -> BlockPos? = { id ->
            if (id in dropCache) dropCache[id]
            else plan.environment.findNearestDrop(id).also {
                dropCache[id] = it
                waitClientTicks(1)
            }
        }
        val cachedFindClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>? = { blocks ->
            val key = blocks.distinct().map { it.registryPath }.sorted().joinToString("|")
            if (key in blockCache) blockCache[key]
            else plan.environment.findClosestBlock(blocks).also {
                blockCache[key] = it
                waitClientTicks(1)
            }
        }
        val cachedFindNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>? = { itemIds ->
            val key = itemIds.sorted().joinToString("|")
            if (key in dropSetCache) dropSetCache[key]
            else plan.environment.findNearestDropAny(itemIds).also {
                dropSetCache[key] = it
            }
        }

        cachedFindNearestDropAny(activeGoal.acceptedItemIds)?.let { (itemId, drop) ->
            val distancePenalty = plan.playerPos.distSqr(drop).coerceAtMost(4096.0) * 0.02
            candidates.add(
                ScoredDecision(
                    PlanDecision.CollectDrop(itemId, activeGoal.acceptedItemIds, drop, score = 950.0 - distancePenalty),
                    950.0 - distancePenalty
                )
            )
        }

        addHarvestCandidates(
            candidates = candidates,
            activeGoal = activeGoal,
            activeRemaining = activeRemaining,
            plan = plan,
            ancestors = ancestors,
            blockedItems = blockedItems,
            findClosestBlock = cachedFindClosestBlock
        )

        addCraftingCandidates(
            candidates = candidates,
            activeGoal = activeGoal,
            activeRemaining = activeRemaining,
            plan = plan,
            ancestors = ancestors,
            blockedItems = blockedItems,
            findNearestDrop = cachedFindNearestDrop,
            findNearestDropAny = cachedFindNearestDropAny,
            findClosestBlock = cachedFindClosestBlock
        )

        addSmeltingCandidates(
            candidates = candidates,
            activeGoal = activeGoal,
            activeRemaining = activeRemaining,
            plan = plan,
            ancestors = ancestors,
            blockedItems = blockedItems,
            findNearestDrop = cachedFindNearestDrop,
            findNearestDropAny = cachedFindNearestDropAny,
            findClosestBlock = cachedFindClosestBlock
        )

        return candidates.maxByOrNull { it.score }?.decision ?: PlanDecision.NoPlan()
    }

    private suspend fun addHarvestCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        val harvestByDrop = activeGoal.acceptedItemIds.mapNotNull { dropId ->
            if (dropId in plan.blockedMineItems) return@mapNotNull null
            val harvestSources = RecipeLookup.getHarvestSourcesForDrop(dropId)
            val harvest = harvestSources.firstOrNull() ?: return@mapNotNull null
            val craftingRecipes = RecipeLookup.getRecipesFor(dropId)
            if (craftingRecipes.isNotEmpty() && harvest.isSelfDrop) return@mapNotNull null
            dropId to harvestSources
        }
        if (harvestByDrop.isEmpty()) return

        val allBlocks = harvestByDrop.flatMap { (_, sources) -> sources.map { it.block } }.distinct()
        val best = findClosestBlock(allBlocks)
        if (best == null) {
            for ((dropId, harvestSources) in harvestByDrop) {
                if (harvestSources.any { !it.isSelfDrop }) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem(dropId, activeGoal.acceptedItemIds, activeGoal.displayName, 1, "need visible source for ${activeGoal.label}", 20.0),
                            20.0
                        )
                    )
                }
            }
            return
        }

        val bestDropIds = harvestByDrop
            .filter { (_, sources) -> sources.any { it.block == best.first } }
            .map { it.first }
            .ifEmpty { listOf(harvestByDrop.first().first) }

        for (dropId in bestDropIds) {
            val blockHarvestInfo = HarvestDatabase.getForBlock(best.first)
            val missingTool = blockHarvestInfo?.let { findMissingTool(it, plan.inventoryCounts) }
            if (missingTool != null) {
                if (missingTool !in blockedItems && missingTool !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem(
                                missingTool,
                                displayName = missingTool,
                                amount = 1,
                                reason = "need $missingTool to mine ${best.first.registryPath}",
                                score = 760.0
                            ),
                            760.0
                        )
                    )
                }
                continue
            }

            val distancePenalty = plan.playerPos.distSqr(best.second).coerceAtMost(4096.0) * 0.015
            candidates.add(
                ScoredDecision(
                    PlanDecision.MineBlock(
                        best.first,
                        best.second,
                        dropId,
                        activeRemaining.coerceAtLeast(1),
                        activeGoal.acceptedItemIds,
                        score = 720.0 - distancePenalty
                    ),
                    720.0 - distancePenalty
                )
            )
        }
    }

    private suspend fun addCraftingCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: suspend (String) -> BlockPos?,
        findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        val craftableGoalIds = activeGoal.acceptedItemIds.filterNot { it in plan.blockedCraftItems }
        if (craftableGoalIds.isEmpty()) return

        var bestCraftPlan: RecipePlan? = null
        for (goalId in craftableGoalIds) {
            for (recipe in RecipeLookup.getRecipesFor(goalId).take(MAX_RECIPE_SEARCH)) {
                val planScore = scoreRecipe(
                    recipe = recipe,
                    targetItemId = goalId,
                    activeGoal = activeGoal,
                    activeRemaining = activeRemaining,
                    inventoryCounts = plan.inventoryCounts,
                    playerPos = plan.playerPos,
                    blockedItems = blockedItems,
                    ancestors = ancestors,
                    findNearestDrop = findNearestDrop,
                    findNearestDropAny = findNearestDropAny,
                    findClosestBlock = findClosestBlock
                )
                if (planScore != null && (bestCraftPlan == null || planScore.score > bestCraftPlan.score)) {
                    bestCraftPlan = planScore
                }
            }
        }
        bestCraftPlan ?: return

        val craftPlan = bestCraftPlan
        if (craftPlan.canCraft) {
            if (craftPlan.recipe.source == ItemSource.CRAFTING_TABLE) {
                val tableNearby = plan.environment.findNearestBlock(Blocks.CRAFTING_TABLE)
                waitClientTicks(1)
                val hasTable = (plan.inventoryCounts[WorkstationIds.CRAFTING_TABLE] ?: 0) > 0
                if (!hasTable && tableNearby == null && WorkstationIds.CRAFTING_TABLE !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem(
                                WorkstationIds.CRAFTING_TABLE,
                                displayName = WorkstationIds.CRAFTING_TABLE,
                                amount = 1,
                                reason = "need crafting table",
                                score = 780.0
                            ),
                            780.0
                        )
                    )
                    return
                }
            }

            candidates.add(
                ScoredDecision(
                    PlanDecision.CraftItem(
                        craftPlan.itemId,
                        craftPlan.recipe,
                        score = 680.0 + craftPlan.score
                    ),
                    680.0 + craftPlan.score
                )
            )
            return
        }

        val need = craftPlan.missing.firstOrNull {
            it.itemId !in ancestors && it.itemId !in blockedItems
        }
        if (need != null) {
            candidates.add(
                    ScoredDecision(
                        PlanDecision.AcquireItem(
                            need.itemId,
                            need.acceptedItemIds,
                            need.displayName,
                            need.amount,
                            "need ${need.label} for crafting ${activeGoal.label}",
                            score = craftPlan.score
                        ),
                    craftPlan.score
                )
            )
        }
    }

    private suspend fun addSmeltingCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: suspend (String) -> BlockPos?,
        findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        val smeltableGoalIds = activeGoal.acceptedItemIds.filterNot { it in plan.blockedCraftItems }
        if (smeltableGoalIds.isEmpty()) return

        val cookingRecipes = RecipeLookup.cookingRecipes.filter {
            it.resultId in smeltableGoalIds
        }

        for (recipe in cookingRecipes.take(MAX_RECIPE_SEARCH)) {
            val batches = batchesNeeded(activeRemaining, recipe.resultCount)
            val missing = missingIngredients(
                recipe = recipe,
                batches = batches,
                inventoryCounts = plan.inventoryCounts,
                activeGoal = activeGoal,
                ancestors = ancestors,
                blockedItems = blockedItems,
                playerPos = plan.playerPos,
                findNearestDrop = findNearestDrop,
                findNearestDropAny = findNearestDropAny,
                findClosestBlock = findClosestBlock
            )
            val canSmelt = missing.isEmpty()

            if (canSmelt) {
                val fuel = findFuel(plan.inventoryCounts)
                if (fuel == null && "coal" !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem("coal", displayName = "coal", amount = 1, reason = "need fuel for smelting ${activeGoal.label}", score = 620.0),
                            620.0
                        )
                    )
                    continue
                }
                val hasFurnace = (plan.inventoryCounts["furnace"] ?: 0) > 0 || plan.environment.findNearestBlock(Blocks.FURNACE) != null
                waitClientTicks(1)
                if (!hasFurnace && "furnace" !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem("furnace", displayName = "furnace", amount = 1, reason = "need furnace for smelting ${activeGoal.label}", score = 610.0),
                            610.0
                        )
                    )
                    continue
                }
                candidates.add(
                    ScoredDecision(
                        PlanDecision.SmeltItem(recipe.resultId, recipe, score = 640.0),
                        640.0
                    )
                )
            } else {
                val need = missing.firstOrNull {
                    it.itemId !in ancestors && it.itemId !in blockedItems
                }
                if (need != null) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem(
                                need.itemId,
                                need.acceptedItemIds,
                                need.displayName,
                                need.amount,
                                "need ${need.label} for smelting ${activeGoal.label}",
                                score = 560.0 + need.sourceScore
                            ),
                            560.0 + need.sourceScore
                        )
                    )
                }
            }
        }
    }

    private data class ScoredDecision(val decision: PlanDecision, val score: Double)

    private data class MissingNeed(
        val itemId: String,
        val acceptedItemIds: Set<String>,
        val displayName: String?,
        val amount: Int,
        val sourceScore: Double
    ) {
        val label: String
            get() = displayName ?: if (acceptedItemIds.size == 1) itemId else acceptedItemIds.joinToString("|")
    }

    private data class RecipePlan(
        val itemId: String,
        val recipe: Recipe,
        val canCraft: Boolean,
        val missing: List<MissingNeed>,
        val score: Double
    )

    private suspend fun scoreRecipe(
        recipe: Recipe,
        targetItemId: String = recipe.resultId,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        inventoryCounts: Map<String, Int>,
        playerPos: BlockPos,
        blockedItems: Set<String>,
        ancestors: Set<String>,
        findNearestDrop: suspend (String) -> BlockPos?,
        findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ): RecipePlan? {
        if (targetItemId in blockedItems) return null

        val batches = batchesNeeded(activeRemaining, recipe.resultCount)
        val missing = missingIngredients(recipe, batches, inventoryCounts, activeGoal, ancestors, blockedItems, playerPos, findNearestDrop, findNearestDropAny, findClosestBlock)
        val canCraft = missing.isEmpty()
        val sourceBonus = when (recipe.source) {
            ItemSource.HAND_CRAFT -> 45.0
            ItemSource.CRAFTING_TABLE -> 25.0
            else -> 0.0
        }
        val missingPenalty = missing.sumOf { it.amount } * 28.0
        val sourceScore = missing.sumOf { it.sourceScore }
        val cyclePenalty = if (missing.any { it.itemId in ancestors || it.itemId in activeGoal.acceptedItemIds }) 500.0 else 0.0
        val craftBonus = if (canCraft) 200.0 else 0.0
        return RecipePlan(
            itemId = targetItemId,
            recipe = recipe,
            canCraft = canCraft,
            missing = missing.sortedByDescending { it.sourceScore - it.amount * 5.0 },
            score = sourceBonus + craftBonus + sourceScore - missingPenalty - cyclePenalty
        )
    }

    private suspend fun missingIngredients(
        recipe: Recipe,
        batches: Int,
        inventoryCounts: Map<String, Int>,
        activeGoal: GoalFrame,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        playerPos: BlockPos,
        findNearestDrop: suspend (String) -> BlockPos?,
        findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ): List<MissingNeed> {
        val simulated = inventoryCounts.toMutableMap()
        val missing = mutableListOf<MissingNeed>()

        for (ingredient in recipe.ingredients) {
            val totalNeeded = ingredient.count * batches
            var remaining = totalNeeded

            val sortedIds = ingredient.itemIds.sortedByDescending { simulated[it] ?: 0 }
            for (id in sortedIds) {
                val have = simulated[id] ?: 0
                if (have <= 0) continue
                val used = minOf(have, remaining)
                simulated[id] = have - used
                remaining -= used
                if (remaining <= 0) break
            }

            if (remaining > 0) {
                val canonicalIngredient = canonicalizeIngredient(ingredient, activeGoal)
                val source = chooseIngredientSource(
                    itemIds = canonicalIngredient.itemIds,
                    displayName = canonicalIngredient.displayName,
                    amount = remaining,
                    activeGoal = activeGoal,
                    ancestors = ancestors,
                    blockedItems = blockedItems,
                    playerPos = playerPos,
                    findNearestDrop = findNearestDrop,
                    findNearestDropAny = findNearestDropAny,
                    findClosestBlock = findClosestBlock
                )
                missing.add(source)
            }
        }

        return missing
    }

    private data class CanonicalIngredient(
        val itemIds: List<String>,
        val displayName: String
    )

    private fun canonicalizeIngredient(ingredient: RecipeIngredient, activeGoal: GoalFrame): CanonicalIngredient {
        if (ingredient.isTag && ingredient.displayName.endsWith("_logs") && isPlanksGoal(activeGoal)) {
            val logItems = TagResolver.getItems("minecraft:logs")
                .mapTo(linkedSetOf()) { it.removePrefix("minecraft:") }
            if (logItems.isNotEmpty()) {
                return CanonicalIngredient(logItems.toList(), "#logs")
            }
        }

        return CanonicalIngredient(
            itemIds = ingredient.itemIds,
            displayName = ingredientDisplayName(ingredient)
        )
    }

    private fun isPlanksGoal(goal: GoalFrame): Boolean {
        if (goal.displayName == "#planks") return true
        return goal.acceptedItemIds.any { it.endsWith("_planks") }
    }

    private suspend fun chooseIngredientSource(
        itemIds: List<String>,
        displayName: String,
        amount: Int,
        activeGoal: GoalFrame,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        playerPos: BlockPos,
        findNearestDrop: suspend (String) -> BlockPos?,
        findNearestDropAny: suspend (Set<String>) -> Pair<String, BlockPos>?,
        findClosestBlock: suspend (List<Block>) -> Pair<Block, BlockPos>?
    ): MissingNeed {
        val candidates = itemIds
            .filterNot { it in blockedItems || it in ancestors || it in activeGoal.acceptedItemIds }
            .ifEmpty { itemIds }

        val ranked = mutableListOf<MissingNeed>()
        findNearestDropAny(candidates.toSet())?.let { (id, drop) ->
            ranked.add(MissingNeed(id, itemIds.toCollection(linkedSetOf()), displayName, amount, 140.0 - distancePenalty(playerPos, drop, 0.02)))
        }

        val harvestOptions = candidates.mapNotNull { id ->
            val sources = RecipeLookup.getHarvestSourcesForDrop(id)
            if (sources.isEmpty()) null else id to sources
        }
        if (harvestOptions.isNotEmpty()) {
            val blocks = harvestOptions.flatMap { (_, sources) -> sources.map { it.block } }.distinct()
            findClosestBlock(blocks)?.let { best ->
                val matchingIds = harvestOptions.filter { (_, sources) -> sources.any { it.block == best.first } }.map { it.first }
                for (id in matchingIds) {
                    ranked.add(MissingNeed(id, itemIds.toCollection(linkedSetOf()), displayName, amount, 115.0 - distancePenalty(playerPos, best.second, 0.015)))
                }
            }
        }

        for (id in candidates) {
            if (ranked.any { it.itemId == id }) continue
            val score = when {
                RecipeLookup.getHarvestSourcesForDrop(id).any { !it.isSelfDrop } -> 45.0
                RecipeLookup.getRecipesFor(id).isNotEmpty() -> 55.0
                RecipeLookup.cookingRecipes.any { it.resultId == id } -> 35.0
                else -> 0.0
            }
            ranked.add(MissingNeed(id, itemIds.toCollection(linkedSetOf()), displayName, amount, score))
        }

        return ranked.maxByOrNull { it.sourceScore }
            ?: MissingNeed(itemIds.firstOrNull() ?: activeGoal.itemId, itemIds.toCollection(linkedSetOf()), displayName, amount, -1000.0)
    }

    private fun ingredientDisplayName(ingredient: RecipeIngredient): String {
        return if (ingredient.isTag) "#${ingredient.displayName}" else ingredient.displayName
    }

    private fun distancePenalty(playerPos: BlockPos, pos: BlockPos, scale: Double): Double =
        playerPos.distSqr(pos).coerceAtMost(4096.0) * scale

    private fun batchesNeeded(amount: Int, resultCount: Int): Int =
        max(1, (amount + resultCount - 1) / resultCount)

    private fun findFuel(inventoryCounts: Map<String, Int>): String? {
        return listOf("coal", "charcoal", "coal_block", "stick", "oak_planks", "spruce_planks", "birch_planks")
            .firstOrNull { (inventoryCounts[it] ?: 0) > 0 }
    }

    fun findDependencyChain(
        targetId: String,
        inventoryCounts: Map<String, Int>
    ): List<ParsedRecipe>? {
        return recipeGraph.findPathTo(targetId, inventoryCounts)
    }

    private fun findMissingTool(
        info: org.kvxd.kiwi.harvest.BlockHarvestInfo,
        inventoryCounts: Map<String, Int>
    ): String? {
        if (!info.requiresCorrectTool) return null
        if (info.toolType == HarvestToolType.NONE || info.toolType == HarvestToolType.ANY) return null

        if (info.toolType == HarvestToolType.SHEARS) {
            return if ((inventoryCounts["shears"] ?: 0) > 0) null else "shears"
        }

        val tierNames = when {
            info.minTier >= HarvestToolTier.NETHERITE -> listOf("netherite")
            info.minTier >= HarvestToolTier.DIAMOND -> listOf("diamond", "netherite")
            info.minTier >= HarvestToolTier.IRON -> listOf("iron", "diamond", "netherite")
            info.minTier >= HarvestToolTier.STONE -> listOf("stone", "iron", "diamond", "netherite")
            else -> listOf("wooden", "stone", "iron", "diamond", "netherite")
        }
        val toolSuffix = when (info.toolType) {
            HarvestToolType.PICKAXE -> "pickaxe"
            HarvestToolType.AXE -> "axe"
            HarvestToolType.SHOVEL -> "shovel"
            HarvestToolType.HOE -> "hoe"
            HarvestToolType.SWORD -> "sword"
            HarvestToolType.SHEARS, HarvestToolType.ANY, HarvestToolType.NONE -> return null
        }
        for (tier in tierNames) {
            val toolId = "${tier}_$toolSuffix"
            if ((inventoryCounts[toolId] ?: 0) > 0) return null
        }
        return "${tierNames.first()}_$toolSuffix"
    }
}
