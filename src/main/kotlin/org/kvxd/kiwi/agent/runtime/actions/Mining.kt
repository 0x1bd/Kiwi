package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.MovementController
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.MiningTargeting
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.isBlockInReach
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.MiningUtil
import org.kvxd.kiwi.util.coroutine.waitClient
import org.kvxd.kiwi.util.math.RaycastHelper
import org.kvxd.kiwi.util.registryPath
import org.kvxd.kiwi.util.math.RotationUtils
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
            DebugState.agentMineTarget = pos
            DebugState.agentMineBlockId = blockInfo.id

            val state = level.getBlockState(pos)
            if (state.isAir) break

            if (state.block != blockInfo.block && state.block !in blockInfo.alternatives) break

            if (!isBlockInReach(pos)) {
                goalPos = moveToMiningStand(pos, fallback = goalPos)
                totalAttempts++
                continue
            }

            MiningUtil.selectBestTool(state)

            val targetPos = aimAtMiningTarget(pos)
            if (targetPos == null) {
                val obstruction = miningRayObstruction(pos, blockInfo)
                if (obstruction != null) {
                    mineObstruction(obstruction)
                    totalAttempts++
                    waitClient(50.milliseconds)
                    continue
                }
                goalPos = moveToMiningStand(pos, fallback = goalPos)
                totalAttempts++
                waitClient(50.milliseconds)
                continue
            }

            if (!isCrosshairOnBlock(pos)) {
                totalAttempts++
                waitClient(50.milliseconds)
                continue
            }

            InputOverride.update { attack = true }
            var innerTicks = 0
            while (!level.getBlockState(pos).isAir && innerTicks < 100) {
                if (!RotationUtils.isLookingAt(targetPos, 0.8) || !isCrosshairOnBlock(pos)) {
                    InputOverride.update { attack = false }
                    break
                }
                waitClient(50.milliseconds)
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
        if (DebugState.agentMineTarget == pos) {
            DebugState.agentMineTarget = null
            DebugState.agentMineBlockId = ""
        }
    }

    if (totalAttempts >= maxOuterAttempts && !level.getBlockState(pos).isAir) {
        throw AgentFailure("Timed out mining $pos after $maxOuterAttempts attempts")
    }

    if (level.getBlockState(pos).isAir) {
        var pickupWait = 0
        val preCount = inventoryCount(blockInfo.dropId)
        while (inventoryCount(blockInfo.dropId) <= preCount && pickupWait < 15) {
            waitClient(50.milliseconds)
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
        waitClient(50.milliseconds)
        alignTicks++
    }
    MovementController.stop()

    return stand
}

private suspend fun aimAtMiningTarget(pos: BlockPos): Vec3? {
    var lastTarget: Vec3? = null
    var aimTicks = 0

    while (aimTicks < 20) {
        val targetPos = visibleMiningPoint(pos) ?: RotationUtils.getClosestPointOnBlock(pos, player.eyePosition)
        lastTarget = targetPos
        val rots = RotationUtils.getLookRotations(targetPos)
        RotationManager.setTarget(rots.x, rots.y)
        RotationManager.tick()

        if (RotationUtils.isLookingAt(targetPos, 0.8) && isCrosshairOnBlock(pos)) {
            return targetPos
        }

        waitClient(50.milliseconds)
        aimTicks++
    }

    return lastTarget.takeIf { isCrosshairOnBlock(pos) }
}

private fun visibleMiningPoint(pos: BlockPos): Vec3? {
    return miningProbePoints(pos).firstOrNull { point ->
        val hit = clipToMiningPoint(point)
        hit.type == HitResult.Type.BLOCK && hit.blockPos == pos
    }
}

private fun miningProbePoints(pos: BlockPos): List<Vec3> {
    val x = pos.x.toDouble()
    val y = pos.y.toDouble()
    val z = pos.z.toDouble()
    return listOf(
        Vec3(x + 0.5, y + 0.5, z + 0.5),
        Vec3(x + 0.5, y + 0.85, z + 0.5),
        Vec3(x + 0.5, y + 0.15, z + 0.5),
        Vec3(x + 0.15, y + 0.5, z + 0.5),
        Vec3(x + 0.85, y + 0.5, z + 0.5),
        Vec3(x + 0.5, y + 0.5, z + 0.15),
        Vec3(x + 0.5, y + 0.5, z + 0.85)
    )
}

private fun clipToMiningPoint(point: Vec3): BlockHitResult {
    return level.clip(
        ClipContext(
            player.eyePosition,
            point,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        )
    )
}

private fun isCrosshairOnBlock(pos: BlockPos): Boolean {
    val hit = RaycastHelper.raycast(1.0f) as? BlockHitResult ?: return false
    return hit.type == HitResult.Type.BLOCK && hit.blockPos == pos
}

private fun miningRayObstruction(target: BlockPos, blockInfo: BlockInfo): BlockPos? {
    var obstruction: BlockPos? = null
    for (point in miningProbePoints(target)) {
        val hit = clipToMiningPoint(point)
        if (hit.type != HitResult.Type.BLOCK) continue
        if (hit.blockPos == target) return null

        val hitPos = hit.blockPos
        val state = level.getBlockState(hitPos)
        if (state.isAir || !CollisionCache.isSolid(hitPos)) continue

        val hitBlock = state.block
        if (hitBlock == blockInfo.block || hitBlock in blockInfo.alternatives) continue

        if (canClearMiningObstruction(hitPos, target, hitBlock)) {
            obstruction = hitPos
        }
    }

    return obstruction
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
