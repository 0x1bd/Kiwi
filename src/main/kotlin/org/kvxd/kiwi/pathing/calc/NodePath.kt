package org.kvxd.kiwi.pathing.calc

import net.minecraft.util.math.BlockPos

class NodePath(private val nodes: List<Node>) {

    var index: Int = 0
        private set

    val size: Int get() = nodes.size
    val isEmpty: Boolean get() = nodes.isEmpty()
    val isFinished: Boolean get() = index >= nodes.size

    fun current(): Node? = nodes.getOrNull(index)

    fun previous(): Node? = nodes.getOrNull(index - 1)

    fun peek(offset: Int = 0): Node? = nodes.getOrNull(index + offset)

    fun next(): Node? = peek(1)

    fun last(): Node? = nodes.lastOrNull()

    fun advance(): Boolean {
        if (index + 1 >= nodes.size) {
            index = nodes.size
            return false
        }
        index++
        return true
    }

    operator fun get(i: Int): Node? = nodes.getOrNull(i)

    fun distanceSqToCurrent(pos: BlockPos): Double {
        val cur = current() ?: return Double.MAX_VALUE
        return pos.getSquaredDistance(cur.pos)
    }

    fun reachedCurrent(pos: BlockPos, thresholdSq: Double = 0.8): Boolean {
        return distanceSqToCurrent(pos) < thresholdSq
    }
}