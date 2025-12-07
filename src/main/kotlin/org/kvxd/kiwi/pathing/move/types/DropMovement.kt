package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object DropMovement : AbstractMovement(MovementType.DROP) {

    private const val BASE_COST = 1.5

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for (dir in Direction.Type.HORIZONTAL) {
            val ledge = start.offset(dir)

            if (CollisionCache.isSolid(ledge) || CollisionCache.isSolid(ledge.up())) continue

            var currentDropPos = ledge
            val maxFall = ConfigManager.data.maxFallHeight

            for (i in 1..maxFall) {
                currentDropPos = currentDropPos.down()

                if (!CollisionCache.isPassable(currentDropPos)) break

                if (CollisionCache.isSolid(currentDropPos.down())) {
                    val cost = BASE_COST + (i * 0.5)
                    output.append(currentDropPos, current, target, cost)
                    break
                }
            }
        }
    }
}