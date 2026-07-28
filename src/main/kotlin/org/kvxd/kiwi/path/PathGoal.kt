package org.kvxd.kiwi.path

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

interface PathGoal {

    fun isReached(x: Int, y: Int, z: Int): Boolean

    fun heuristic(x: Int, y: Int, z: Int): Double

    fun approximateTarget(): BlockPos

    fun describe(): String
}

class GoalBlock(private val target: BlockPos) : PathGoal {
    override fun isReached(x: Int, y: Int, z: Int) = x == target.x && y == target.y && z == target.z
    override fun heuristic(x: Int, y: Int, z: Int) = euclidean(x, y, z, target)
    override fun approximateTarget(): BlockPos = target
    override fun describe() = "block(${target.x},${target.y},${target.z})"
}

class GoalNear(private val target: BlockPos, private val radius: Double) : PathGoal {
    private val radiusSq = radius * radius
    override fun isReached(x: Int, y: Int, z: Int) = target.distSqr(BlockPos(x, y, z)) <= radiusSq
    override fun heuristic(x: Int, y: Int, z: Int) = (euclidean(x, y, z, target) - radius).coerceAtLeast(0.0)
    override fun approximateTarget(): BlockPos = target
    override fun describe() = "near(${target.x},${target.y},${target.z},r=$radius)"
}

class GoalXZ(private val x: Int, private val z: Int, private val referenceY: Int) : PathGoal {
    override fun isReached(x: Int, y: Int, z: Int) = x == this.x && z == this.z
    override fun heuristic(x: Int, y: Int, z: Int): Double {
        val dx = (x - this.x).toDouble()
        val dz = (z - this.z).toDouble()
        return sqrt(dx * dx + dz * dz)
    }

    override fun approximateTarget(): BlockPos = BlockPos(x, referenceY, z)
    override fun describe() = "xz($x,$z)"
}

class GoalAdjacent(private val target: BlockPos, private val reach: Double = 4.0) : PathGoal {
    private val reachSq = reach * reach

    override fun isReached(x: Int, y: Int, z: Int): Boolean {
        val dx = (x + 0.5) - (target.x + 0.5)
        val dy = (y + 1.62) - (target.y + 0.5)
        val dz = (z + 0.5) - (target.z + 0.5)
        return dx * dx + dy * dy + dz * dz <= reachSq
    }

    override fun heuristic(x: Int, y: Int, z: Int) = (euclidean(x, y, z, target) - reach).coerceAtLeast(0.0)
    override fun approximateTarget(): BlockPos = target
    override fun describe() = "adjacent(${target.x},${target.y},${target.z})"
}

class GoalPickup(private val target: BlockPos) : PathGoal {

    override fun isReached(x: Int, y: Int, z: Int): Boolean {
        if (kotlin.math.abs(x - target.x) > 1) return false
        if (kotlin.math.abs(z - target.z) > 1) return false
        return y <= target.y && y >= target.y - 2
    }

    override fun heuristic(x: Int, y: Int, z: Int) = euclidean(x, y, z, target)
    override fun approximateTarget(): BlockPos = target
    override fun describe() = "pickup(${target.x},${target.y},${target.z})"
}

class GoalAny(private val goals: List<PathGoal>) : PathGoal {
    override fun isReached(x: Int, y: Int, z: Int) = goals.any { it.isReached(x, y, z) }
    override fun heuristic(x: Int, y: Int, z: Int) = goals.minOf { it.heuristic(x, y, z) }
    override fun approximateTarget(): BlockPos = goals.first().approximateTarget()
    override fun describe() = goals.joinToString("|") { it.describe() }
}

class GoalInverted(private val inner: PathGoal, private val minDistance: Double) : PathGoal {
    override fun isReached(x: Int, y: Int, z: Int) =
        euclidean(x, y, z, inner.approximateTarget()) >= minDistance

    override fun heuristic(x: Int, y: Int, z: Int) =
        max(0.0, minDistance - euclidean(x, y, z, inner.approximateTarget()))

    override fun approximateTarget(): BlockPos = inner.approximateTarget()
    override fun describe() = "away(${inner.describe()},$minDistance)"
}

private fun euclidean(x: Int, y: Int, z: Int, target: BlockPos): Double {
    val dx = (x - target.x).toDouble()
    val dy = (y - target.y).toDouble()
    val dz = (z - target.z).toDouble()
    return sqrt(dx * dx + dy * dy + dz * dz)
}

internal fun manhattanY(a: Int, b: Int): Int = abs(a - b)
