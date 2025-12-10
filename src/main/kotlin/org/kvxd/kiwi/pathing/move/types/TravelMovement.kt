package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
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
        val offset = start.add(dx, 0, dz)
        val cost = if (isDiagonal) COST_DIAGONAL else COST_FLAT

        if (CollisionCache.isWalkable(offset)) {
            if (isDiagonal) {
                if (CollisionCache.isSolid(start.add(dx, 0, 0)) ||
                    CollisionCache.isSolid(start.add(0, 0, dz))) {
                    return
                }

                if (!CollisionCache.isSolid(start.add(dx, 0, 0).down()) ||
                    !CollisionCache.isSolid(start.add(0, 0, dz).down())) {
                    return
                }
            }

            output.append(offset, current, target, cost, MovementType.TRAVEL)
            return
        }

        if (!isDiagonal) {
            if (CollisionCache.isPassable(start.up(2)) &&
                CollisionCache.isSolid(offset) &&
                CollisionCache.isPassable(offset.up()) &&
                CollisionCache.isPassable(offset.up(2))
            ) {
                output.append(offset.up(), current, target, COST_JUMP, MovementType.JUMP)
                return
            }

            if (CollisionCache.isPassable(offset) &&
                CollisionCache.isPassable(offset.up()) &&
                CollisionCache.isPassable(offset.down()) &&
                CollisionCache.isSolid(offset.down(2))
            ) {
                output.append(offset.down(), current, target, COST_FLAT, MovementType.DROP)
            }
        }
    }
}