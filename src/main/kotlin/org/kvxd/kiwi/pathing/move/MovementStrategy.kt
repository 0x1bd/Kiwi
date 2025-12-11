package org.kvxd.kiwi.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import kotlin.math.sqrt

interface MovementStrategy {

    fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>)

    fun getStartNode(start: BlockPos): Node? = null
}

abstract class AbstractMovement(private val defaultType: MovementType) : MovementStrategy {

    protected fun MutableList<Node>.append(
        pos: BlockPos,
        parent: Node,
        target: BlockPos,
        baseCost: Double,
        typeOverride: MovementType? = null
    ) {
        val g = parent.costG + baseCost
        val h = sqrt(pos.distSqr(target))
        this.add(Node(pos, parent, g, h, typeOverride ?: defaultType))
    }
}