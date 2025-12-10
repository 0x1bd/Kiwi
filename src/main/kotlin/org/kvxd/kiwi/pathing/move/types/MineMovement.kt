package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement
import org.kvxd.kiwi.util.MiningUtil

object MineMovement : AbstractMovement(MovementType.MINE) {

    private const val TIME_PENALTY = 6.0

    private const val BASE_MINING_COST = 15.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigData.allowBreak) return

        val start = current.pos

        for (dir in Direction.entries) {
            val dest = start.offset(dir)

            if (CollisionCache.isWalkable(dest) && dir != Direction.DOWN) continue

            val blocksToBreak = getBlocksToBreak(start, dir)

            if (blocksToBreak.isEmpty()) continue

            var totalTime = 0.0
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

                totalTime += time
            }

            if (possible) {
                var cost = BASE_MINING_COST + (totalTime * TIME_PENALTY)

                if (dir == Direction.UP) {
                    cost += 5.0
                }

                output.append(dest, current, target, cost)
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
                if (CollisionCache.isSolid(dest.up())) list.add(dest.up())
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