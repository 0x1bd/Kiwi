package org.kvxd.kiwi

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.util.math.RaycastHelper

val client: Minecraft
    get() = Minecraft.getInstance()!!

val player: LocalPlayer
    get() = client.player!!

val level: Level
    get() = client.level!!

fun isBlockInReach(pos: BlockPos): Boolean {
    val reach = player.blockInteractionRange()
    val blockBox = AABB(pos)
    return blockBox.distanceToSqr(player.eyePosition) <= reach * reach
}

fun hasLineOfSightToBlock(pos: BlockPos): Boolean {
    val eye = player.eyePosition
    val center = Vec3.atCenterOf(pos)

    val targets = listOf(
        center,
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z.toDouble()),
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 1.0),
        Vec3(pos.x.toDouble(), pos.y + 0.5, pos.z + 0.5),
        Vec3(pos.x + 1.0, pos.y + 0.5, pos.z + 0.5),
        Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5),
        Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
    )

    return targets.any { target ->
        val hit = level.clip(
            ClipContext(
                eye,
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )
        hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && hit.blockPos == pos
    }
}

fun canInteractWithBlock(pos: BlockPos): Boolean {
    return isBlockInReach(pos) && hasLineOfSightToBlock(pos)
}

fun isCrosshairOnBlock(pos: BlockPos): Boolean {
    val hit = RaycastHelper.raycast(1.0f)
    return hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && hit.blockPos == pos
}