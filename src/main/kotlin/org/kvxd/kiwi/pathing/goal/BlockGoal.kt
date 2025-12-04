package org.kvxd.kiwi.pathing.goal

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.move.Physics
import kotlin.math.sqrt

class BlockGoal(private val target: BlockPos) : Goal {

    override fun hasReached(pos: BlockPos): Boolean {
        if (pos == target) return true

        if (!ConfigManager.data.strictPosition) {
            if (pos == target.up()) {
                return Physics.isSolid(target)
            }
        }

        return false
    }

    override fun getHeuristic(pos: BlockPos): Double {
        return sqrt(pos.getSquaredDistance(target))
    }

    override fun getApproximateTarget(): BlockPos = target
}