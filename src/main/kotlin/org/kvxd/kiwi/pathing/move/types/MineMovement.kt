package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement
import org.kvxd.kiwi.util.MiningUtil

object MineMovement : AbstractMovement(MovementType.MINE) {

    private const val PENALTY = 3.0
    private const val BASE_WALK_COST = 1.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigManager.data.allowBreak) return

        val start = current.pos

        for (dir in Direction.entries) {
            val dest = start.offset(dir)

            if (CollisionCache.isWalkable(dest) && dir != Direction.DOWN) continue

            val blocksToBreak = getBlocksToBreak(start, dir)

            if (blocksToBreak.isEmpty()) continue

            var totalCost = 0.0
            var possible = true

            for (pos in blocksToBreak) {
                if (!CollisionCache.isSafeToMine(pos)) {
                    possible = false
                    break
                }

                val time = MiningUtil.getBreakTime(pos)

                if (time.isInfinite()) {
                    possible = false
                    break
                }

                if (isFloodRisk(pos)) {
                    possible = false
                    break
                }

                totalCost += time
            }

            if (possible && totalCost > 0) {
                output.append(dest, current, target, BASE_WALK_COST + (totalCost * PENALTY))
            }
        }
    }

    private fun getBlocksToBreak(start: BlockPos, dir: Direction): List<BlockPos> {
        val dest = start.offset(dir)
        val list = ArrayList<BlockPos>(2)

        when (dir) {
            Direction.UP -> {
                if (CollisionCache.isSolid(dest)) list.add(dest)
                if (CollisionCache.isSolid(dest.up())) list.add(dest.up())
            }

            Direction.DOWN -> {
                if (CollisionCache.isSolid(dest)) list.add(dest)
            }

            else -> {
                if (CollisionCache.isSolid(dest)) list.add(dest)
                if (CollisionCache.isSolid(dest.up())) list.add(dest.up())
            }
        }

        return list
    }

    private fun isFloodRisk(pos: BlockPos): Boolean {
        if (CollisionCache.isDangerous(pos.up())) return true

        for (side in Direction.Type.HORIZONTAL) {
            if (CollisionCache.isDangerous(pos.offset(side))) return true
        }

        return false
    }

}