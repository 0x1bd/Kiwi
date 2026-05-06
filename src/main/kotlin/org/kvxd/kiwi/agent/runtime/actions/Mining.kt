package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.MiningTargeting
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.isBlockInReach
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.registryPath
import org.kvxd.kiwi.util.math.RotationUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

data class BlockInfo(val block: Block, val alternatives: List<Block> = emptyList(), val dropId: String = block.registryPath) {
    val id: String get() = block.registryPath
}

private const val MINING_STAND_REACH = 0.75

suspend fun AgentRuntime.mineBlock(blockInfo: BlockInfo, pos: BlockPos) {
    phase = AgentPhase.MINING
    var goalPos = moveToMiningStand(pos)

    val maxOuterAttempts = 5
    var totalAttempts = 0

    try {
        while (totalAttempts < maxOuterAttempts) {
            val state = level.getBlockState(pos)
            if (state.isAir) break

            if (state.block != blockInfo.block && state.block !in blockInfo.alternatives) break

            if (!isBlockInReach(pos)) {
                val cleared = clearObstruction(pos, blockInfo)
                goalPos = moveToMiningStand(pos, fallback = goalPos)
                totalAttempts++
                if (!cleared) delay(50.milliseconds)
                continue
            }

            MiningUtil.selectBestTool(state)

            val targetPos = RotationUtils.getClosestPointOnBlock(pos, player.eyePosition)
            val rots = RotationUtils.getLookRotations(targetPos)
            RotationManager.setTarget(rots.x, rots.y)

            var aimTicks = 0
            while (!RotationUtils.isLookingAt(targetPos, 0.5) && aimTicks < 20) {
                delay(50.milliseconds)
                aimTicks++
            }

            val obstruction = currentHitObstruction(pos, blockInfo)
            if (obstruction != null) {
                mineObstruction(obstruction)
                totalAttempts++
                delay(50.milliseconds)
                continue
            }

            if (!RotationUtils.isLookingAt(targetPos, 0.5)) {
                goalPos = moveToMiningStand(pos, fallback = goalPos)
                totalAttempts++
                delay(50.milliseconds)
                continue
            }

            InputOverride.update { attack = true }
            var innerTicks = 0
            while (!level.getBlockState(pos).isAir && innerTicks < 100) {
                if (!RotationUtils.isLookingAt(targetPos, 0.5)) {
                    InputOverride.update { attack = false }
                    break
                }
                delay(50.milliseconds)
                innerTicks++
            }
            InputOverride.update { attack = false }

            if (level.getBlockState(pos).isAir) break

            if (!isBlockInReach(pos) || innerTicks >= 40) {
                goalPos = moveToMiningStand(pos, fallback = goalPos)
            }
            totalAttempts++
        }
    } finally {
        InputOverride.update { attack = false }
        InputOverride.release()
    }

    if (totalAttempts >= maxOuterAttempts && !level.getBlockState(pos).isAir) {
        throw AgentFailure("Timed out mining $pos after $maxOuterAttempts attempts")
    }

    if (level.getBlockState(pos).isAir) {
        var pickupWait = 0
        val preCount = inventoryCount(blockInfo.dropId)
        while (inventoryCount(blockInfo.dropId) <= preCount && pickupWait < 15) {
            delay(50.milliseconds)
            pickupWait++
        }
    }
}

private suspend fun AgentRuntime.moveToMiningStand(target: BlockPos, fallback: BlockPos? = null): BlockPos {
    val stand = MiningTargeting.findStandPosition(target)
        ?: fallback
        ?: throw AgentFailure("No reachable mining stand for $target")

    walkTo(stand, reach = MINING_STAND_REACH)
    InputOverride.capture()

    var alignTicks = 0
    while (!MovementController.alignToBlockCenter(stand) && alignTicks < 20) {
        delay(50.milliseconds)
        alignTicks++
    }
    MovementController.stop()

    return stand
}

private fun currentHitObstruction(target: BlockPos, blockInfo: BlockInfo): BlockPos? {
    val hit = client.hitResult as? BlockHitResult ?: return null
    if (hit.type != HitResult.Type.BLOCK) return null

    val hitPos = hit.blockPos
    if (hitPos == target) return null

    val state = level.getBlockState(hitPos)
    if (state.isAir || !CollisionCache.isSolid(hitPos)) return null

    val hitBlock = state.block
    if (hitBlock == blockInfo.block || hitBlock in blockInfo.alternatives) return null

    return hitPos.takeIf { canClearMiningObstruction(it, target, hitBlock) }
}

private suspend fun AgentRuntime.mineObstruction(pos: BlockPos) {
    val hitBlock = level.getBlockState(pos).block
    try {
        mineBlock(BlockInfo(hitBlock, dropId = hitBlock.registryPath), pos)
    } catch (_: AgentFailure) {
    } finally {
        InputOverride.update { attack = false }
    }
}

private suspend fun AgentRuntime.clearObstruction(target: BlockPos, blockInfo: BlockInfo): Boolean {
    val hit = client.hitResult
    if (hit is BlockHitResult) {
        val hitPos = hit.blockPos
        if (hitPos == target) return true
        val hitState = level.getBlockState(hitPos)
        if (hitState.isAir) return true

        val hitBlock = hitState.block
        if (hitBlock == blockInfo.block || hitBlock in blockInfo.alternatives) return false

        if (CollisionCache.isSolid(hitPos) && canClearMiningObstruction(hitPos, target, hitBlock)) {
            mineObstruction(hitPos)
            return true
        }
    }
    return false
}

private fun canClearMiningObstruction(pos: BlockPos, target: BlockPos, block: Block): Boolean {
    if (!CollisionCache.isSolid(pos)) return false
    if (CollisionCache.isLeaf(pos)) return true
    if (block in ConfigData.safeToMineBlockTypes) return true
    return isSameColumnDigDownObstruction(pos, target)
}

private fun isSameColumnDigDownObstruction(pos: BlockPos, target: BlockPos): Boolean {
    val playerPos = player.blockPosition()
    if (target.y >= playerPos.y) return false
    if (pos.x != target.x || pos.z != target.z) return false
    return pos.y in (target.y + 1)..playerPos.y
}