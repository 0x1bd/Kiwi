package org.kvxd.kiwi.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

object WorldUtils {

    fun placeBlockBelow(pos: BlockPos) {
        val placePos = pos.below()
        val placeAABB = AABB(placePos)

        if (player.boundingBox.intersects(placeAABB)) return

        val hitResult = BlockHitResult(
            placePos.center,
            Direction.UP,
            placePos.below(),
            false
        )

        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
        player.swing(InteractionHand.MAIN_HAND)
    }

}