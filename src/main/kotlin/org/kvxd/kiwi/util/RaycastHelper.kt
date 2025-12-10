package org.kvxd.kiwi.util

import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import org.kvxd.kiwi.control.RotationManager
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player

object RaycastHelper {

    fun raycast(tickDelta: Float): HitResult {
        val blockReach = player.blockInteractionRange()
        val entityReach = player.entityInteractionRange()
        val maxReach = blockReach.coerceAtLeast(entityReach)

        val cameraPos = player.getEyePosition(tickDelta)

        val yRot = if (RotationManager.hasTarget) RotationManager.targetYRot else player.yRot
        val xRot = if (RotationManager.hasTarget) RotationManager.targetXRot else player.xRot

        val rotationVec = RotationUtils.getRotationVector(xRot, yRot)
        val rayEnd = cameraPos.add(rotationVec.multiply(maxReach, maxReach, maxReach))

        var target: HitResult = level.clip(
            ClipContext(
                cameraPos,
                rayEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        var distanceToBlock = maxReach
        if (target.type != HitResult.Type.MISS) {
            distanceToBlock = target.location.distanceTo(cameraPos)
        }

        val entityBox = player.boundingBox
            .expandTowards(rotationVec.multiply(maxReach, maxReach, maxReach))
            .inflate(1.0, 1.0, 1.0)

        val entityHitResult = ProjectileUtil.getEntityHitResult(
            player,
            cameraPos,
            rayEnd,
            entityBox,
            { entity -> !entity.isSpectator && entity.isPickable },
            distanceToBlock * distanceToBlock
        )

        if (entityHitResult != null) {
            val distanceToEntity = cameraPos.distanceTo(entityHitResult.location)
            if (distanceToEntity < distanceToBlock && distanceToEntity < entityReach) {
                target = entityHitResult
            }
        }

        return target
    }
}