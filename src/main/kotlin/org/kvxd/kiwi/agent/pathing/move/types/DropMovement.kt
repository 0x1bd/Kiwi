package org.kvxd.kiwi.agent.pathing.move.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.move.AbstractMovement
import org.kvxd.kiwi.agent.pathing.move.MovementObstructionUtil

object DropMovement : AbstractMovement(MovementType.DROP) {

    private const val BASE_COST = 1.5

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for (dir in Direction.Plane.HORIZONTAL) {
            val ledge = start.relative(dir)
            val dropBlocksToBreak = mutableListOf(ledge, ledge.above())

            var currentDropPos = ledge

            for (i in 1..256) {
                currentDropPos = currentDropPos.below()
                dropBlocksToBreak.add(currentDropPos)
                dropBlocksToBreak.add(currentDropPos.above())

                val landingBlock = currentDropPos.below()

                if (CollisionCache.hasState(landingBlock, CollisionCache.WATER)) {
                    if (ConfigData.allowWater) {
                        val miningPlan = MovementObstructionUtil.planMining(dropBlocksToBreak)
                        if (miningPlan != null) {
                            val cost = BASE_COST + (i * 0.2)
                            output.append(currentDropPos, current, target, cost, miningPlan = miningPlan)
                        }
                    }
                    break
                }

                if (CollisionCache.isSolid(landingBlock)) {
                    if (i <= ConfigData.maxFallHeight) {
                        val miningPlan = MovementObstructionUtil.planMining(dropBlocksToBreak)
                        if (miningPlan != null) {
                            val cost = BASE_COST + (i * 0.5)
                            output.append(currentDropPos, current, target, cost, miningPlan = miningPlan)
                        }
                    }
                    break
                }
            }
        }
    }
}