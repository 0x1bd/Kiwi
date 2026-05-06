package org.kvxd.kiwi.agent.pathing.move.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.capability.MovementCapability
import org.kvxd.kiwi.agent.capability.MovementCapabilities
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.PathSearchDiagnostics
import org.kvxd.kiwi.agent.pathing.move.AbstractMovement
import org.kvxd.kiwi.agent.pathing.move.MovementCosts
import org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil

object TravelMovement : AbstractMovement(MovementType.TRAVEL) {

    private val DIAGONAL_OFFSETS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val CARDINAL_OFFSETS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    override fun getStartNode(start: BlockPos): Node? {
        if (!CollisionCache.hasState(start, CollisionCache.WATER)) return null
        if (!MovementCapabilities.require(MovementCapability.WATER_TRAVERSAL)) {
            PathSearchDiagnostics.require(MovementCapability.WATER_TRAVERSAL)
            return null
        }

        var currentPos = start
        var currentNode = Node(start, null, 0.0, 0.0, MovementType.WATER_WALK)

        if (isSurfaceWater(start)) return currentNode

        for (i in 0 until 64) {
            val up = currentPos.above()
            if (CollisionCache.isSolid(up) || CollisionCache.isDangerous(up)) break

            if (isSurfaceWater(up)) {
                currentNode = Node(
                    up,
                    currentNode,
                    currentNode.costG + 2.0,
                    0.0,
                    MovementType.WATER_WALK
                )
                return currentNode
            }

            if (!CollisionCache.hasState(up, CollisionCache.WATER)) {
                return currentNode
            }

            currentPos = up
        }

        return null
    }

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

        val waterNode = when {
            isSurfaceWater(offset) -> offset
            isSurfaceWater(offset.below()) -> offset.below()
            else -> null
        }
        if (waterNode != null) {
            if (!MovementCapabilities.require(MovementCapability.WATER_TRAVERSAL)) {
                PathSearchDiagnostics.require(MovementCapability.WATER_TRAVERSAL)
                return
            }

            if (isDiagonal && !isSafeDiagonal(start, dx, dz)) return

            val cost = if (isDiagonal) MovementCosts.WATER_DIAGONAL else MovementCosts.WATER
            output.append(waterNode, current, target, cost, MovementType.WATER_WALK)
            return
        }

        val cost = if (isDiagonal) MovementCosts.DIAGONAL else MovementCosts.FLAT

        if (CollisionCache.isSolid(offset.below())) {
            val blocksToBreak = listOf(offset, offset.above())
            val miningPlan = MovementObstructionUtil.planMining(blocksToBreak)

            if (miningPlan != null) {
                if (isDiagonal && !isSafeDiagonal(start, dx, dz)) return
                output.append(offset, current, target, cost, MovementType.TRAVEL, miningPlan)
                return
            }
        }

        if (!isDiagonal && CollisionCache.isSolid(offset)) {
            val blocksToBreak = listOf(start.above(2), offset.above(), offset.above(2))
            val miningPlan = MovementObstructionUtil.planMining(blocksToBreak)

            if (miningPlan != null && offset !in miningPlan.blocks) {
                output.append(offset.above(), current, target, MovementCosts.STEP_UP, MovementType.JUMP, miningPlan)
            }
        }
    }

    private fun isSurfaceWater(pos: BlockPos): Boolean {
        return CollisionCache.hasState(pos, CollisionCache.WATER) &&
            !CollisionCache.hasState(pos.above(), CollisionCache.WATER) &&
            CollisionCache.isPassable(pos.above())
    }

    private fun isSafeDiagonal(start: BlockPos, dx: Int, dz: Int): Boolean {
        val n1 = start.offset(dx, 0, 0)
        val n2 = start.offset(0, 0, dz)

        if (CollisionCache.isObstructed(n1) || CollisionCache.isObstructed(n2)) return false

        val n1Up = n1.above()
        val n2Up = n2.above()
        if (CollisionCache.isObstructed(n1Up) || CollisionCache.isObstructed(n2Up)) return false

        val safe1 = CollisionCache.isSolid(n1.below()) || isSurfaceWater(n1)
        val safe2 = CollisionCache.isSolid(n2.below()) || isSurfaceWater(n2)
        return safe1 && safe2
    }
}
