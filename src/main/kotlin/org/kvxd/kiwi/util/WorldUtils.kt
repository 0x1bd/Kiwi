package org.kvxd.kiwi.util

import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.kvxd.kiwi.client
import org.kvxd.kiwi.player

object WorldUtils {

    fun placeBlockBelow(pos: BlockPos) {
        val placePos = pos.down()
        val placeBox = Box(placePos)

        if (player.boundingBox.intersects(placeBox)) return

        val hitResult = BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos.down(),
            false
        )

        client.interactionManager?.interactBlock(player, Hand.MAIN_HAND, hitResult)
        player.swingHand(Hand.MAIN_HAND)
    }

}