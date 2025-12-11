package org.kvxd.kiwi.pathing.move.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object DropMovement : AbstractMovement(MovementType.DROP) {

    private const val BASE_COST = 1.5

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for (dir in Direction.Plane.HORIZONTAL) {
            val ledge = start.relative(dir)

            if (CollisionCache.isSolid(ledge) || CollisionCache.isSolid(ledge.above())) continue

            var currentDropPos = ledge

            for (i in 1..256) {
                currentDropPos = currentDropPos.below()

                if (!CollisionCache.isPassable(currentDropPos)) break

                val landingBlock = currentDropPos.below()

                if (CollisionCache.hasState(landingBlock, CollisionCache.WATER)) {
                    if (ConfigData.allowWater) {
                        val cost = BASE_COST + (i * 0.2)
                        output.append(currentDropPos, current, target, cost)
                    }

                    break
                }

                if (CollisionCache.isSolid(landingBlock)) {
                    if (i <= ConfigData.maxFallHeight) {
                        val cost = BASE_COST + (i * 0.5)
                        output.append(currentDropPos, current, target, cost)
                    }
                    break
                }
            }
        }
    }
}