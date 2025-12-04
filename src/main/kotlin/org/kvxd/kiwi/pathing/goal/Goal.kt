package org.kvxd.kiwi.pathing.goal

import net.minecraft.util.math.BlockPos

interface Goal {

    fun hasReached(pos: BlockPos): Boolean

    fun getHeuristic(pos: BlockPos): Double

    fun getApproximateTarget(): BlockPos

}