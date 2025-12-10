package org.kvxd.kiwi.pathing.move.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object TravelMovement : AbstractMovement(MovementType.TRAVEL) {

    private const val COST_FLAT = 1.0
    private const val COST_DIAGONAL = 1.414
    private const val COST_JUMP = 1.2

    private val DIAGONAL_OFFSETS = arrayOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )

    private val CARDINAL_OFFSETS = arrayOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1
    )

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for ((dx, dz) in CARDINAL_OFFSETS) {
            handleMove(start, dx, dz, output, current, target, false)
        }

        for ((dx, dz) in DIAGONAL_OFFSETS) {
            handleMove(start, dx, dz, output, current, target, true)
        }
    }

    private fun handleMove(
        start: BlockPos,
        dx: Int,
        dz: Int,
        output: MutableList<Node>,
        current: Node,
        target: BlockPos,
        isDiagonal: Boolean
    ) {
        val offset = start.offset(dx, 0, dz)
        val cost = if (isDiagonal) COST_DIAGONAL else COST_FLAT

        if (CollisionCache.isWalkable(offset)) {
            if (isDiagonal) {
                if (CollisionCache.isSolid(start.offset(dx, 0, 0)) ||
                    CollisionCache.isSolid(start.offset(0, 0, dz))
                ) {
                    return
                }

                if (!CollisionCache.isSolid(start.offset(dx, 0, 0).below()) ||
                    !CollisionCache.isSolid(start.offset(0, 0, dz).below())
                ) {
                    return
                }
            }

            output.append(offset, current, target, cost, MovementType.TRAVEL)
            return
        }

        if (!isDiagonal) {
            if (CollisionCache.isPassable(start.above(2)) &&
                CollisionCache.isSolid(offset) &&
                CollisionCache.isPassable(offset.above()) &&
                CollisionCache.isPassable(offset.above(2))
            ) {
                output.append(offset.above(), current, target, COST_JUMP, MovementType.JUMP)
                return
            }

            if (CollisionCache.isPassable(offset) &&
                CollisionCache.isPassable(offset.above()) &&
                CollisionCache.isPassable(offset.below()) &&
                CollisionCache.isSolid(offset.below(2))
            ) {
                output.append(offset.below(), current, target, COST_FLAT, MovementType.DROP)
            }
        }
    }
}