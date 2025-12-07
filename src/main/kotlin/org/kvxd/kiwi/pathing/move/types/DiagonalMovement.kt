package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object DiagonalMovement : AbstractMovement(MovementType.DIAGONAL) {

    private const val COST = 1.4142

    private val OFFSETS = arrayOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val pos = current.pos

        for ((dx, dz) in OFFSETS) {
            val dest = current.pos.add(dx, 0, dz)

            if (!CollisionCache.isWalkable(dest)) continue

            if (CollisionCache.isSolid(pos.x + dx, pos.y, pos.z) || CollisionCache.isSolid(
                    pos.x + dx,
                    pos.y + 1,
                    pos.z
                )
            ) continue
            if (CollisionCache.isSolid(pos.x, pos.y, pos.z + dz) || CollisionCache.isSolid(
                    pos.x,
                    pos.y + 1,
                    pos.z + dz
                )
            ) continue

            output.append(dest, current, target, COST)
        }
    }
}