package org.kvxd.kiwi.agent.runtime

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.Agent
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.canInteractWithBlock
import org.kvxd.kiwi.player

object MiningTargeting {

    fun canMineFromCurrentOrStand(target: BlockPos): Boolean {
        if (canInteractWithBlock(target)) return true
        return findStandPosition(target) != null
    }

    fun findStandPosition(target: BlockPos): BlockPos? {
        val playerPos = player.blockPosition()
        val reach = player.blockInteractionRange()
        val reachSq = reach * reach
        val targetBox = AABB(target)

        return candidateStandPositions(target)
            .filter { CollisionCache.isWalkable(it) }
            .filter { stand ->
                val dx = stand.x - target.x
                val dz = stand.z - target.z
                if (dx * dx + dz * dz > 4.0) return@filter false
                val eye = Vec3(stand.x + 0.5, stand.y + player.eyeHeight.toDouble(), stand.z + 0.5)
                targetBox.distanceToSqr(eye) <= reachSq
            }
            .minByOrNull { standScore(it, target, playerPos) }
    }

    private fun standScore(stand: BlockPos, target: BlockPos, playerPos: BlockPos): Double {
        var score = stand.distSqr(playerPos)
        val support = stand.below()

        if (support in Agent.context.placedPositions || support in Agent.context.minedPositions) {
            score += 16.0
        }

        if (stand.x == target.x && stand.z == target.z && stand.y > target.y + 1) {
            score += 4.0
        }

        return score
    }

    private fun candidateStandPositions(target: BlockPos): Sequence<BlockPos> = sequence {
        for (dy in 1..4) {
            yield(target.above(dy))
        }

        for (dy in -3..1) {
            for (dx in -4..4) {
                for (dz in -4..4) {
                    if (dx == 0 && dz == 0) continue
                    yield(target.offset(dx, dy, dz))
                }
            }
        }
    }
}
