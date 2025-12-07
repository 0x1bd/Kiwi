package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object JumpMovement : AbstractMovement(MovementType.JUMP) {

    private const val COST = 2.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (CollisionCache.isSolid(current.pos.up(2))) return

        for (dir in Direction.Type.HORIZONTAL) {
            val dest = current.pos.offset(dir).up()

            if (CollisionCache.isWalkable(dest) && CollisionCache.isSolid(dest.down())) {
                output.append(dest, current, target, COST)
            }
        }
    }
}