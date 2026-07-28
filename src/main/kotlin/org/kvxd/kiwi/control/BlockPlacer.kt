package org.kvxd.kiwi.control

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.client
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player

enum class PlaceResult {
    PLACED,
    NEEDS_AIM,
    NO_SUPPORT,
    NO_ITEM
}

object BlockPlacer {

    fun place(target: BlockPos, accept: (ItemStack) -> Boolean): PlaceResult {
        if (!level.getBlockState(target).canBeReplaced()) return PlaceResult.PLACED
        if (!equipPlaceable(accept)) return PlaceResult.NO_ITEM

        val support = supportFace(target) ?: return PlaceResult.NO_SUPPORT
        val (againstPos, face) = support
        val hitPoint = Vec3(
            againstPos.x + 0.5 + face.stepX * 0.5,
            againstPos.y + 0.5 + face.stepY * 0.5,
            againstPos.z + 0.5 + face.stepZ * 0.5
        )

        LookController.lookAt(hitPoint)
        if (!LookController.isAimedAt(hitPoint, 6.0)) return PlaceResult.NEEDS_AIM

        val gameMode = client.gameMode ?: return PlaceResult.NO_SUPPORT
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(hitPoint, face, againstPos, false))
        player.swing(InteractionHand.MAIN_HAND)
        return PlaceResult.PLACED
    }

    private fun equipPlaceable(accept: (ItemStack) -> Boolean): Boolean {
        val current = player.inventory.getItem(player.inventory.selectedSlot)
        if (current.item is BlockItem && accept(current)) return true
        return Containers.equip { stack -> !stack.isEmpty && stack.item is BlockItem && accept(stack) }
    }

    private fun supportFace(target: BlockPos): Pair<BlockPos, Direction>? {
        for (direction in Direction.entries) {
            val neighbour = target.relative(direction)
            val state = level.getBlockState(neighbour)
            if (state.isAir) continue
            if (runCatching { state.getCollisionShape(level, neighbour).isEmpty }.getOrDefault(true)) continue
            if (!BlockBreaker.isInReach(neighbour)) continue
            return neighbour to direction.opposite
        }
        return null
    }
}
