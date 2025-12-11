package org.kvxd.kiwi.pathing.move.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object TravelMovement : AbstractMovement(MovementType.TRAVEL) {

    private const val COST_FLAT = 1.0
    private const val COST_DIAGONAL = 1.414
    private const val COST_JUMP = 1.2

    private const val COST_WATER = 1.5
    private const val COST_WATER_DIAGONAL = 2.12
    private const val COST_WATER_UP = 2.0
    private const val MAX_BREATH_DIST = 15

    private val DIAGONAL_OFFSETS = arrayOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )

    private val CARDINAL_OFFSETS = arrayOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1
    )

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos
        val inWater = CollisionCache.hasState(start, CollisionCache.WATER)

        for ((dx, dz) in CARDINAL_OFFSETS) {
            handleMove(start, dx, dz, output, current, target, false)
        }

        for ((dx, dz) in DIAGONAL_OFFSETS) {
            handleMove(start, dx, dz, output, current, target, true)
        }

        if (inWater || CollisionCache.hasState(start.above(), CollisionCache.WATER)) {
            if (!ConfigData.allowWater) return

            val up = start.above()
            if (CollisionCache.hasState(up, CollisionCache.WATER)) {
                if (getDistanceToSurface(up) <= MAX_BREATH_DIST) {
                    output.append(up, current, target, COST_WATER_UP, MovementType.WATER_WALK)
                }
            }
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

        if (CollisionCache.hasState(offset, CollisionCache.WATER)) {
            if (!ConfigData.allowWater) return

            val cost = if (isDiagonal) COST_WATER_DIAGONAL else COST_WATER

            if (isDiagonal) {
                if (CollisionCache.isSolid(start.offset(dx, 0, 0)) ||
                    CollisionCache.isSolid(start.offset(0, 0, dz))
                ) {
                    return
                }
                val n1 = start.offset(dx, 0, 0)
                val n2 = start.offset(0, 0, dz)
                val safe1 = CollisionCache.isSolid(n1.below()) || CollisionCache.hasState(n1, CollisionCache.WATER)
                val safe2 = CollisionCache.isSolid(n2.below()) || CollisionCache.hasState(n2, CollisionCache.WATER)

                if (!safe1 || !safe2) return
            }

            output.append(offset, current, target, cost, MovementType.WATER_WALK)
            return
        }

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
            } else if (CollisionCache.isPassable(offset) &&
                CollisionCache.isPassable(offset.above()) &&
                CollisionCache.hasState(offset.below(), CollisionCache.WATER)
            ) {
                if (!ConfigData.allowWater) return

                output.append(offset.below(), current, target, COST_FLAT, MovementType.WATER_WALK)
            }
        }
    }

    private fun getDistanceToSurface(start: BlockPos): Int {
        var dist = 0
        var p = start
        while (dist <= MAX_BREATH_DIST) {
            if (!CollisionCache.hasState(p, CollisionCache.WATER)) {
                if (CollisionCache.isPassable(p)) return dist
                return Int.MAX_VALUE
            }
            p = p.above()
            dist++
        }
        return Int.MAX_VALUE
    }
}