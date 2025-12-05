package org.kvxd.kiwi.control.movement.impl

import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.client
import org.kvxd.kiwi.control.input.InputOverride
import org.kvxd.kiwi.control.movement.MergeableAction
import org.kvxd.kiwi.player

data class Interact(
    val action: Interaction,
    val pos: BlockPos? = null,
    val face: Direction? = null
) : MergeableAction {

    override fun merge(other: MergeableAction): MergeableAction? {
        if (other !is Interact) return null

        if (action == Interaction.LEFT_CLICK || other.action == Interaction.LEFT_CLICK) {
            return other.takeIf { it.action == Interaction.LEFT_CLICK } ?: this
        }

        if (action == Interaction.RIGHT_CLICK || other.action == Interaction.RIGHT_CLICK) {
            return other.takeIf { it.action == Interaction.RIGHT_CLICK } ?: this
        }

        return other
    }

    override fun execute() {
        when (action) {
            Interaction.LEFT_CLICK -> InputOverride.state.attack = true
            Interaction.RIGHT_CLICK -> InputOverride.state.use = true
            Interaction.USE_ITEM -> client.interactionManager?.interactItem(player, Hand.MAIN_HAND)
        }
    }
}

enum class Interaction { LEFT_CLICK, RIGHT_CLICK, USE_ITEM }