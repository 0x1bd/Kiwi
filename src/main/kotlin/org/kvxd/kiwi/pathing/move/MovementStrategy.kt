package org.kvxd.kiwi.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import kotlin.math.sqrt

interface MovementStrategy {

    fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>)

    fun createNode(pos: BlockPos, parent: Node, target: BlockPos, type: MovementType, costMod: Double): Node {
        val g = parent.costG + costMod
        val h = sqrt(pos.getSquaredDistance(target))
        return Node(pos, parent, g, h, type)
    }
}

abstract class AbstractMovement(private val type: MovementType) : MovementStrategy {

    abstract fun getCost(current: Node, dest: BlockPos): Double

    protected fun addIfValid(current: Node, target: BlockPos, dest: BlockPos, output: MutableList<Node>) {
        val cost = getCost(current, dest)

        if (cost != Double.POSITIVE_INFINITY) {
            output.add(createNode(dest, current, target, type, cost))
        }
    }
}