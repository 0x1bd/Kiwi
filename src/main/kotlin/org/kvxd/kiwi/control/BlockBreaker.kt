package org.kvxd.kiwi.control

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.client
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player

enum class BreakProgress {
    APPROACHING,

    BREAKING,

    DONE,

    IMPOSSIBLE
}

object BlockBreaker {

    private var target: BlockPos? = null

    val currentTarget: BlockPos? get() = target

    fun stop() {
        if (target != null) {
            runCatching { client.gameMode?.stopDestroyBlock() }
            target = null
        }
    }

    fun tick(pos: BlockPos): BreakProgress {
        val state = level.getBlockState(pos)
        if (state.isAir) {
            stop()
            return BreakProgress.DONE
        }
        if (state.getDestroySpeed(level, pos) < 0f) return BreakProgress.IMPOSSIBLE

        val gameMode = client.gameMode ?: return BreakProgress.IMPOSSIBLE

        if (!isInReach(pos)) {
            if (target == pos) stop()
            return BreakProgress.APPROACHING
        }

        val hit = visibleFace(pos)
        if (hit == null) {
            if (target == pos) stop()
            return BreakProgress.APPROACHING
        }

        ToolSelector.equipBest(state)
        LookController.lookAt(hit.location)

        if (!LookController.isAimedAt(hit.location, 4.0)) {
            return BreakProgress.APPROACHING
        }

        if (target != pos) {
            stop()
            gameMode.startDestroyBlock(pos, hit.direction)
            target = pos
        } else {
            gameMode.continueDestroyBlock(pos, hit.direction)
        }
        player.swing(InteractionHand.MAIN_HAND)

        return if (level.getBlockState(pos).isAir) {
            stop()
            BreakProgress.DONE
        } else {
            BreakProgress.BREAKING
        }
    }

    fun isInReach(pos: BlockPos): Boolean {
        val reach = player.blockInteractionRange()
        return net.minecraft.world.phys.AABB(pos).distanceToSqr(player.eyePosition) <= reach * reach
    }

    fun visibleFace(pos: BlockPos): BlockHitResult? {
        val eye = player.eyePosition
        for (point in probePoints(pos)) {
            val hit = level.clip(
                ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
            )
            if (hit.type == HitResult.Type.BLOCK && hit.blockPos == pos) return hit
        }
        return null
    }

    private fun probePoints(pos: BlockPos): List<Vec3> {
        val shape = level.getBlockState(pos).getShape(level, pos)
        val bounds = if (shape.isEmpty) {
            net.minecraft.world.phys.AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        } else {
            shape.bounds()
        }
        val cx = pos.x + (bounds.minX + bounds.maxX) / 2.0
        val cy = pos.y + (bounds.minY + bounds.maxY) / 2.0
        val cz = pos.z + (bounds.minZ + bounds.maxZ) / 2.0

        val points = ArrayList<Vec3>(7)
        points.add(Vec3(cx, cy, cz))
        for (direction in Direction.entries) {
            points.add(
                Vec3(
                    cx + direction.stepX * (bounds.xsize / 2.0 - 0.02),
                    cy + direction.stepY * (bounds.ysize / 2.0 - 0.02),
                    cz + direction.stepZ * (bounds.zsize / 2.0 - 0.02)
                )
            )
        }
        return points
    }
}
