package org.kvxd.baobab.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.baobab.pathing.calc.MovementType
import org.kvxd.baobab.pathing.calc.Node
import kotlin.math.sqrt

interface MovementStrategy {

    fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>)

    fun createNode(pos: BlockPos, parent: Node, target: BlockPos, type: MovementType, costMod: Double): Node {
        val g = parent.costG + costMod
        val h = sqrt(pos.getSquaredDistance(target))

        return Node(pos, parent, g, h, type)
    }
}