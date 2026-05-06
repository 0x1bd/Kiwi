package org.kvxd.kiwi.agent.runtime

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.ScanUtil
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.job.AgentRequest
import org.kvxd.kiwi.agent.job.GoalAgenda
import org.kvxd.kiwi.agent.job.GoalFrame
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.InventoryUtil

class AgentRuntime(
    val agent: Agent,
    val request: AgentRequest
) {
    var phase: AgentPhase = AgentPhase.IDLE
        set(value) {
            field = value
            updateDebug()
        }

    val currentPhase: String
        get() = phase.label

    var failures: Int = 0
    var currentPlan: String = ""
    var currentPlanScore: Double = 0.0

    private val agenda = GoalAgenda(
        inventoryCount = ::inventoryCount,
        isBlocked = { itemId -> itemId in agent.context.blockedCraftItems || itemId in agent.context.blockedMineItems },
        onChanged = ::updateDebug
    )

    val goals: List<GoalFrame>
        get() = agenda.items

    val topGoalLabel: String
        get() = agenda.topLabel

    fun start() {
        agenda.seed(request)
        updateDebug()
    }

    fun isRequestComplete(): Boolean = inventoryCount(request.itemId) >= request.amount

    fun inventoryCount(itemId: String): Int = agent.countItemInInventory(itemId)

    fun inventoryCounts(): Map<String, Int> {
        return InventoryUtil.fullInventory
            .asSequence()
            .filterNot { it.isEmpty }
            .groupingBy { BuiltInRegistries.ITEM.getKey(it.item).path }
            .fold(0) { count, stack -> count + stack.count }
    }

    fun findNearestDrop(itemId: String): BlockPos? =
        ScanUtil.findNearestDroppedItem(itemId = itemId, radius = ConfigData.dropScanRadius)
            ?.blockPosition()

    fun findNearestBlock(block: Block): BlockPos? {
        val remembered = agent.findRememberedBlock(block)
        if (remembered != null && !isFailedBlock(block, remembered) && isReachableBlock(remembered)) {
            return remembered
        }

        return findNearestReachableBlock(block)
    }

    fun findClosestBlock(blocks: List<Block>): Pair<Block, BlockPos>? {
        val blockSet = blocks.toSet()
        if (blockSet.isEmpty()) return null

        val found = findClosestMineableBlock(blockSet) ?: findDigDownTarget(blockSet) ?: return null
        return found.block to found.pos
    }

    fun pushGoal(itemId: String, amount: Int, reason: String): GoalAgenda.PushResult {
        return agenda.push(itemId, amount, reason)
    }

    fun popGoal(): GoalFrame? = agenda.pop()

    fun popCompletedGoals() {
        agenda.popCompleted()
    }

    fun remainingFor(goal: GoalFrame): Int = agenda.remainingFor(goal)

    fun targetCountFor(goal: GoalFrame): Int = agenda.targetCountFor(goal)

    fun topGoal(): GoalFrame? = agenda.top()

    fun markFailedBlock(block: Block, pos: BlockPos) {
        agent.context.markFailedBlock(block, pos)
    }

    fun isFailedBlock(block: Block, pos: BlockPos): Boolean {
        return agent.context.isFailedBlock(block, pos)
    }

    fun updateDebug() {
        DebugState.agentPhase = phase.label
        DebugState.agentObjective = request.itemId
        DebugState.agentObjectiveAmount = request.amount
        DebugState.agentGoalCount = goals.size
        DebugState.agentGoalTop = topGoalLabel
        DebugState.agentPlanFailures = failures
        DebugState.agentKnownBlocks = agent.context.knownBlocks.values.sumOf { it.size }
    }

    fun cleanup() {
        PathNavigator.stop()
        InputOverride.release()
        RotationManager.reset()
    }

    private fun findNearestReachableBlock(blockType: Block): BlockPos? {
        val origin = player.blockPosition()
        val radius = ConfigData.blockScanRadius

        val result = ScanUtil.scanNearby(
            origin = origin,
            radius = radius,
            blockFilter = { block -> block == blockType },
            maxResults = 20
        )

        val reachable = result.targets
            .filter { !isFailedBlock(blockType, it.pos) && isReachableBlock(it.pos) }
            .minByOrNull { it.distance }

        return reachable?.pos
    }

    private fun isReachableBlock(pos: BlockPos): Boolean {
        val playerPos = player.blockPosition()

        if (pos.y - playerPos.y > 6) return false
        if (pos.y - playerPos.y < -4) return false

        val dx = pos.x - playerPos.x
        val dz = pos.z - playerPos.z
        val horizontalSq = dx * dx + dz * dz
        if (horizontalSq > 64 * 64) return false

        return true
    }

    private fun findClosestMineableBlock(blocks: Set<Block>): ScanUtil.BlockScanTarget? {
        val origin = player.blockPosition()
        val radius = ConfigData.blockScanRadius
        val minY = (origin.y - radius).coerceAtLeast(level.dimensionType().minY())
        val maxY = (origin.y + radius).coerceAtMost(level.dimensionType().minY() + level.dimensionType().height())

        for (r in 0..radius) {
            var best: ScanUtil.BlockScanTarget? = null

            val minX = origin.x - r
            val maxX = origin.x + r
            val minZ = origin.z - r
            val maxZ = origin.z + r
            val minYS = (origin.y - r).coerceAtLeast(minY)
            val maxYS = (origin.y + r).coerceAtMost(maxY)

            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (r > 0 && x != minX && x != maxX && z != minZ && z != maxZ) continue

                    for (y in minYS..maxYS) {
                        val pos = BlockPos(x, y, z)
                        val block = level.getBlockState(pos).block
                        if (block !in blocks) continue

                        if (isFailedBlock(block, pos) || !isReachableBlock(pos) || !MiningTargeting.canMineFromCurrentOrStand(pos)) continue

                        val dx = x - origin.x
                        val dy = y - origin.y
                        val dz = z - origin.z
                        val distance = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                        val candidate = ScanUtil.BlockScanTarget(pos, block, distance)
                        if (best == null || candidate.distance < best.distance) {
                            best = candidate
                        }
                    }
                }
            }

            if (best != null) return best
        }

        return null
    }

    private fun findDigDownTarget(blocks: Set<Block>): ScanUtil.BlockScanTarget? {
        val origin = player.blockPosition()
        val minY = level.dimensionType().minY()
        val maxDepth = 18

        for (depth in 1..maxDepth) {
            val y = origin.y - depth
            if (y < minY) break

            for (horizontalRadius in 0..2) {
                var best: ScanUtil.BlockScanTarget? = null
                for (dx in -horizontalRadius..horizontalRadius) {
                    for (dz in -horizontalRadius..horizontalRadius) {
                        if (horizontalRadius > 0 && kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dz)) != horizontalRadius) {
                            continue
                        }

                        val pos = BlockPos(origin.x + dx, y, origin.z + dz)
                        val block = level.getBlockState(pos).block
                        if (block !in blocks) continue

                        if (isFailedBlock(block, pos) || MiningTargeting.findStandPosition(pos) == null) continue

                        val distance = kotlin.math.sqrt(origin.distSqr(pos))
                        val candidate = ScanUtil.BlockScanTarget(pos, block, distance)
                        if (best == null || candidate.distance < best.distance) {
                            best = candidate
                        }
                    }
                }
                if (best != null) return best
            }
        }

        return null
    }
}
