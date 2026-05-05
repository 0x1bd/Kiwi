package org.kvxd.kiwi.agent.pathing.goal.goals

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.pathing.goal.Goal
import kotlin.math.abs
import kotlin.math.sqrt

class GoalNearPos(private val target: Vec3, private val radius: Double = 0.35) : Goal {

    override fun hasReached(pos: BlockPos): Boolean {
        val center = target
        val playerPos = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        val dx = playerPos.x - center.x
        val dy = playerPos.y - center.y
        val dz = playerPos.z - center.z
        return sqrt(dx * dx + dy * dy + dz * dz) <= radius
    }

    override fun getHeuristic(pos: BlockPos): Double {
        val dx = pos.x + 0.5 - target.x
        val dy = pos.y - target.y
        val dz = pos.z + 0.5 - target.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    override fun getApproximateTarget(): BlockPos = BlockPos(target.x.toInt(), target.y.toInt(), target.z.toInt())
}