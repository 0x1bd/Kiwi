package org.kvxd.kiwi.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import kotlin.math.sqrt

interface MovementStrategy {

    fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>)
}

abstract class AbstractMovement(private val type: MovementType) : MovementStrategy {

    protected fun MutableList<Node>.append(
        pos: BlockPos,
        parent: Node,
        target: BlockPos,
        baseCost: Double
    ) {
        val g = parent.costG + baseCost
        val h = sqrt(pos.getSquaredDistance(target))
        this.add(Node(pos, parent, g, h, type))
    }
}