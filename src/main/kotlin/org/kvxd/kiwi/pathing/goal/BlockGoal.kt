package org.kvxd.kiwi.pathing.goal

import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

class BlockGoal(private val target: BlockPos) : Goal {

    override fun hasReached(pos: BlockPos): Boolean {
        return pos == target
    }

    override fun getHeuristic(pos: BlockPos): Double {
        return sqrt(pos.getSquaredDistance(target))
    }

    override fun getApproximateTarget(): BlockPos = target
}