package org.kvxd.kiwi.agent.planning

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.agent.ItemSource
import org.kvxd.kiwi.agent.Recipe
import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.agent.WorkstationIds
import org.kvxd.kiwi.agent.job.AgentRequest
import org.kvxd.kiwi.agent.job.GoalFrame
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.harvest.HarvestToolTier
import org.kvxd.kiwi.harvest.HarvestToolType
import org.kvxd.kiwi.recipe.ParsedRecipe
import org.kvxd.kiwi.recipe.RecipeGraph
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
        val findNearestDrop: (String) -> BlockPos?,
        val findNearestBlock: (Block) -> BlockPos?,
        val findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    )

    sealed class PlanDecision {
        abstract val reason: String
        abstract val score: Double

        data class CollectDrop(
            val itemId: String,
            val nearestDrop: BlockPos?,
            override val reason: String = "nearby drop",
            override val score: Double = 0.0
        ) : PlanDecision()

        data class MineBlock(
            val block: Block,
            val targetPos: BlockPos,
            val dropId: String,
            val count: Int = 1,
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
            val amount: Int,
            override val reason: String,
            override val score: Double = 0.0
        ) : PlanDecision()

        data class NoPlan(
            override val reason: String = "no available source",
            override val score: Double = 0.0
        ) : PlanDecision()
    }

    fun nextStep(plan: PlanRequest): PlanDecision {
        val activeGoal = plan.activeGoal
        val activeRemaining = activeGoal.remaining(plan.inventoryCounts[activeGoal.itemId] ?: 0)
        if (activeRemaining <= 0) return PlanDecision.NoPlan("goal already complete")

        val candidates = mutableListOf<ScoredDecision>()
        val ancestors = plan.goals.dropLast(1).mapTo(mutableSetOf()) { it.itemId }
        val blockedItems = plan.blockedCraftItems + plan.blockedMineItems
        val dropCache = mutableMapOf<String, BlockPos?>()
        val blockCache = mutableMapOf<String, Pair<Block, BlockPos>?>()
        val cachedFindNearestDrop: (String) -> BlockPos? = { id ->
            dropCache.getOrPut(id) { plan.environment.findNearestDrop(id) }
        }
        val cachedFindClosestBlock: (List<Block>) -> Pair<Block, BlockPos>? = { blocks ->
            val key = blocks.distinct().map { it.registryPath }.sorted().joinToString("|")
            blockCache.getOrPut(key) { plan.environment.findClosestBlock(blocks) }
        }

        val drop = cachedFindNearestDrop(activeGoal.itemId)
        if (drop != null) {
            val distancePenalty = plan.playerPos.distSqr(drop).coerceAtMost(4096.0) * 0.02
            candidates.add(
                ScoredDecision(
                    PlanDecision.CollectDrop(activeGoal.itemId, drop, score = 950.0 - distancePenalty),
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
            findClosestBlock = cachedFindClosestBlock
        )

        return candidates.maxByOrNull { it.score }?.decision ?: PlanDecision.NoPlan()
    }

    private fun addHarvestCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        if (activeGoal.itemId in plan.blockedMineItems) return

        val harvestSources = RecipeLookup.getHarvestSourcesForDrop(activeGoal.itemId)
        val harvest = harvestSources.firstOrNull() ?: return
        val craftingRecipes = RecipeLookup.getRecipesFor(activeGoal.itemId)
        if (craftingRecipes.isNotEmpty() && harvest.isSelfDrop) return

        val blocks = harvestSources.map { it.block }.distinct()
        val best = findClosestBlock(blocks) ?: plan.environment.findNearestBlock(harvest.block)?.let { harvest.block to it }
        if (best == null) {
            if (harvestSources.any { !it.isSelfDrop }) {
                candidates.add(
                    ScoredDecision(
                        PlanDecision.AcquireItem(activeGoal.itemId, 1, "need visible source for ${activeGoal.itemId}", 20.0),
                        20.0
                    )
                )
            }
            return
        }

        val blockHarvestInfo = HarvestDatabase.getForBlock(best.first)
        val missingTool = blockHarvestInfo?.let { findMissingTool(it, plan.inventoryCounts) }
        if (missingTool != null) {
            if (missingTool !in blockedItems && missingTool !in ancestors) {
                candidates.add(
                    ScoredDecision(
                        PlanDecision.AcquireItem(
                            missingTool,
                            1,
                            "need $missingTool to mine ${best.first.registryPath}",
                            score = 760.0
                        ),
                        760.0
                    )
                )
            }
            return
        }

        val distancePenalty = plan.playerPos.distSqr(best.second).coerceAtMost(4096.0) * 0.015
        candidates.add(
            ScoredDecision(
                PlanDecision.MineBlock(
                    best.first,
                    best.second,
                    activeGoal.itemId,
                    activeRemaining.coerceAtLeast(1),
                    score = 720.0 - distancePenalty
                ),
                720.0 - distancePenalty
            )
        )
    }

    private fun addCraftingCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        if (activeGoal.itemId in plan.blockedCraftItems) return

        val bestCraftPlan = RecipeLookup.getRecipesFor(activeGoal.itemId)
            .asSequence()
            .take(MAX_RECIPE_SEARCH)
            .mapNotNull { recipe ->
                scoreRecipe(
                    recipe = recipe,
                    activeGoal = activeGoal,
                    activeRemaining = activeRemaining,
                    inventoryCounts = plan.inventoryCounts,
                    blockedItems = blockedItems,
                    ancestors = ancestors,
                    findNearestDrop = findNearestDrop,
                    findClosestBlock = findClosestBlock
                )
            }
            .maxByOrNull { it.score }
            ?: return

        if (bestCraftPlan.canCraft) {
            if (bestCraftPlan.recipe.source == ItemSource.CRAFTING_TABLE) {
                val tableNearby = plan.environment.findNearestBlock(Blocks.CRAFTING_TABLE)
                val hasTable = (plan.inventoryCounts[WorkstationIds.CRAFTING_TABLE] ?: 0) > 0
                if (!hasTable && tableNearby == null && WorkstationIds.CRAFTING_TABLE !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem(
                                WorkstationIds.CRAFTING_TABLE,
                                1,
                                "need crafting table",
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
                        activeGoal.itemId,
                        bestCraftPlan.recipe,
                        score = 680.0 + bestCraftPlan.score
                    ),
                    680.0 + bestCraftPlan.score
                )
            )
            return
        }

        val need = bestCraftPlan.missing.firstOrNull {
            it.itemId !in ancestors && it.itemId !in blockedItems
        }
        if (need != null) {
            candidates.add(
                ScoredDecision(
                    PlanDecision.AcquireItem(
                        need.itemId,
                        need.amount,
                        "need ${need.itemId} for crafting ${activeGoal.itemId}",
                        score = bestCraftPlan.score
                    ),
                    bestCraftPlan.score
                )
            )
        }
    }

    private fun addSmeltingCandidates(
        candidates: MutableList<ScoredDecision>,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        plan: PlanRequest,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ) {
        if (activeGoal.itemId in plan.blockedCraftItems) return

        val cookingRecipes = RecipeLookup.cookingRecipes.filter {
            it.resultId == activeGoal.itemId
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
                findNearestDrop = findNearestDrop,
                findClosestBlock = findClosestBlock
            )
            val canSmelt = missing.isEmpty()

            if (canSmelt) {
                val fuel = findFuel(plan.inventoryCounts)
                if (fuel == null && "coal" !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem("coal", 1, "need fuel for smelting ${activeGoal.itemId}", 620.0),
                            620.0
                        )
                    )
                    continue
                }
                val hasFurnace = (plan.inventoryCounts["furnace"] ?: 0) > 0 || plan.environment.findNearestBlock(Blocks.FURNACE) != null
                if (!hasFurnace && "furnace" !in ancestors) {
                    candidates.add(
                        ScoredDecision(
                            PlanDecision.AcquireItem("furnace", 1, "need furnace for smelting ${activeGoal.itemId}", 610.0),
                            610.0
                        )
                    )
                    continue
                }
                candidates.add(
                    ScoredDecision(
                        PlanDecision.SmeltItem(activeGoal.itemId, recipe, score = 640.0),
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
                                need.amount,
                                "need ${need.itemId} for smelting ${activeGoal.itemId}",
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
        val amount: Int,
        val sourceScore: Double
    )

    private data class RecipePlan(
        val recipe: Recipe,
        val canCraft: Boolean,
        val missing: List<MissingNeed>,
        val score: Double
    )

    private fun scoreRecipe(
        recipe: Recipe,
        activeGoal: GoalFrame,
        activeRemaining: Int,
        inventoryCounts: Map<String, Int>,
        blockedItems: Set<String>,
        ancestors: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ): RecipePlan? {
        if (activeGoal.itemId in blockedItems) return null

        val batches = batchesNeeded(activeRemaining, recipe.resultCount)
        val missing = missingIngredients(recipe, batches, inventoryCounts, activeGoal, ancestors, blockedItems, findNearestDrop, findClosestBlock)
        val canCraft = missing.isEmpty()
        val sourceBonus = when (recipe.source) {
            ItemSource.HAND_CRAFT -> 45.0
            ItemSource.CRAFTING_TABLE -> 25.0
            else -> 0.0
        }
        val missingPenalty = missing.sumOf { it.amount } * 28.0
        val sourceScore = missing.sumOf { it.sourceScore }
        val cyclePenalty = if (missing.any { it.itemId in ancestors || it.itemId == activeGoal.itemId }) 500.0 else 0.0
        val craftBonus = if (canCraft) 200.0 else 0.0
        return RecipePlan(
            recipe = recipe,
            canCraft = canCraft,
            missing = missing.sortedByDescending { it.sourceScore - it.amount * 5.0 },
            score = sourceBonus + craftBonus + sourceScore - missingPenalty - cyclePenalty
        )
    }

    private fun missingIngredients(
        recipe: Recipe,
        batches: Int,
        inventoryCounts: Map<String, Int>,
        activeGoal: GoalFrame,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
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
                val source = chooseIngredientSource(
                    itemIds = ingredient.itemIds,
                    amount = remaining,
                    activeGoal = activeGoal,
                    ancestors = ancestors,
                    blockedItems = blockedItems,
                    findNearestDrop = findNearestDrop,
                    findClosestBlock = findClosestBlock
                )
                missing.add(source)
            }
        }

        return missing
    }

    private fun chooseIngredientSource(
        itemIds: List<String>,
        amount: Int,
        activeGoal: GoalFrame,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ): MissingNeed {
        val ranked = itemIds.map { id ->
            val score = scoreSource(id, activeGoal, ancestors, blockedItems, findNearestDrop, findClosestBlock)
            MissingNeed(id, amount, score)
        }
        return ranked.maxByOrNull { it.sourceScore } ?: MissingNeed(itemIds.firstOrNull() ?: activeGoal.itemId, amount, -1000.0)
    }

    private fun scoreSource(
        itemId: String,
        activeGoal: GoalFrame,
        ancestors: Set<String>,
        blockedItems: Set<String>,
        findNearestDrop: (String) -> BlockPos?,
        findClosestBlock: (List<Block>) -> Pair<Block, BlockPos>?
    ): Double {
        if (itemId == activeGoal.itemId || itemId in ancestors) return -800.0
        if (itemId in blockedItems) return -900.0
        if (findNearestDrop(itemId) != null) return 120.0

        val harvestSources = RecipeLookup.getHarvestSourcesForDrop(itemId)
        if (harvestSources.isNotEmpty()) {
            val blocks = harvestSources.map { it.block }.distinct()
            if (findClosestBlock(blocks) != null) return 95.0
            if (harvestSources.any { !it.isSelfDrop }) return 45.0
        }

        val recipes = RecipeLookup.getRecipesFor(itemId)
        if (recipes.isNotEmpty()) return 55.0

        if (RecipeLookup.cookingRecipes.any { it.resultId == itemId }) return 35.0
        return 0.0
    }

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