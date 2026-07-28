package org.kvxd.kiwi.test.suites

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.path.GoalNear
import org.kvxd.kiwi.path.PathSearch
import org.kvxd.kiwi.path.PathStatus
import org.kvxd.kiwi.task.TaskStatus
import org.kvxd.kiwi.task.tasks.NavigateTask
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check
import org.kvxd.kiwi.test.fromClient
import org.kvxd.kiwi.test.onClient

object NavigationSuite {

    private const val MAX_REVERSALS = 6

    fun run(world: BotTestWorld) {
        world.gamemode("survival")
        world.clearInventory()

        flatWalk(world)
        slabStaircase(world)
        jumpStaircase(world)
        wallWithDoorway(world)
        descend(world)
        narrowLedgeDrop(world)
        tunnelThroughWall(world)
        motionQuality(world)
        searchPerformance(world)
    }

    private fun flatWalk(world: BotTestWorld) {
        val origin = world.arena(sizeX = 40, sizeZ = 16)
        val start = BlockPos(origin.x + 1, origin.y + 1, origin.z + 4)
        val goal = BlockPos(origin.x + 30, origin.y + 1, origin.z + 4)

        world.teleport(start)
        world.expectSuccess(NavigateTask(GoalNear(goal, 1.5)), timeoutTicks = 20 * 40, what = "flat walk")

        val at = world.playerPos()
        check(at.distSqr(goal) <= 9.0) { "flat walk ended at $at, wanted $goal" }
        Kiwi.logger.info("Kiwi test: flat walk arrived at $at")
    }

    private fun slabStaircase(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 16)
        val baseY = origin.y + 1
        val z = origin.z + 4

        for (step in 0 until 8) {
            val x = origin.x + 4 + step
            val fullBlocks = step / 2
            for (fill in 0 until fullBlocks) {
                world.setBlock(BlockPos(x, baseY + fill, z), "minecraft:stone")
            }
            val slabType = if (step % 2 == 0) "bottom" else "top"
            world.setBlock(BlockPos(x, baseY + fullBlocks, z), "minecraft:stone_slab[type=$slabType]")
        }
        world.settle(6)

        val start = BlockPos(origin.x + 1, baseY, z)
        val top = BlockPos(origin.x + 11, baseY + 4, z)
        world.setBlock(BlockPos(top.x, baseY + 3, z), "minecraft:stone")
        world.fill(BlockPos(top.x + 1, baseY + 3, z), BlockPos(top.x + 3, baseY + 3, z), "minecraft:stone")
        world.settle(4)

        world.teleport(start)
        world.expectSuccess(
            NavigateTask(GoalNear(BlockPos(top.x + 2, baseY + 4, z), 1.5)),
            timeoutTicks = 20 * 45,
            what = "slab staircase"
        )

        val at = world.playerPos()
        check(at.y >= baseY + 4) { "should have climbed the slab staircase, ended at $at" }
        Kiwi.logger.info("Kiwi test: slab staircase climbed to $at")
    }

    private fun jumpStaircase(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 16)
        val baseY = origin.y + 1
        val z = origin.z + 4

        for (step in 1..4) {
            val x = origin.x + 4 + step
            world.fill(
                BlockPos(x, baseY, z),
                BlockPos(x, baseY + step - 1, z),
                "minecraft:stone"
            )
        }
        val topY = baseY + 4
        world.fill(BlockPos(origin.x + 9, baseY, z), BlockPos(origin.x + 12, topY - 1, z), "minecraft:stone")
        world.settle(6)

        world.teleport(BlockPos(origin.x + 1, baseY, z))
        world.expectSuccess(
            NavigateTask(GoalNear(BlockPos(origin.x + 11, topY, z), 1.5)),
            timeoutTicks = 20 * 45,
            what = "jump staircase"
        )

        val at = world.playerPos()
        check(at.y >= topY) { "should have jumped up the staircase, ended at $at" }
        Kiwi.logger.info("Kiwi test: jump staircase climbed to $at")
    }

    private fun wallWithDoorway(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 24)
        val baseY = origin.y + 1
        val wallX = origin.x + 12

        world.fill(
            BlockPos(wallX, baseY, origin.z),
            BlockPos(wallX, baseY + 3, origin.z + 20),
            "minecraft:bedrock"
        )
        world.fill(
            BlockPos(wallX, baseY, origin.z + 16),
            BlockPos(wallX, baseY + 1, origin.z + 16),
            "minecraft:air"
        )
        world.settle(6)

        val start = BlockPos(origin.x + 2, baseY, origin.z + 4)
        val goal = BlockPos(origin.x + 22, baseY, origin.z + 4)
        world.teleport(start)

        world.expectSuccess(NavigateTask(GoalNear(goal, 2.0)), timeoutTicks = 20 * 60, what = "wall doorway")

        val at = world.playerPos()
        check(at.x > wallX) { "should have gone through the gap, ended at $at" }
        Kiwi.logger.info("Kiwi test: routed through the doorway to $at")
    }

    private fun descend(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1
        val z = origin.z + 4

        world.fill(
            BlockPos(origin.x + 1, baseY, z - 2),
            BlockPos(origin.x + 6, baseY + 2, z + 2),
            "minecraft:stone"
        )
        world.clear(BlockPos(origin.x + 1, baseY + 3, z - 2), BlockPos(origin.x + 6, baseY + 8, z + 2))
        world.settle(6)

        val start = BlockPos(origin.x + 3, baseY + 3, z)
        val goal = BlockPos(origin.x + 14, baseY, z)
        world.teleport(start)

        world.expectSuccess(NavigateTask(GoalNear(goal, 1.5)), timeoutTicks = 20 * 40, what = "descend")

        val at = world.playerPos()
        check(at.y <= baseY + 1) { "should have descended to ground level, ended at $at" }
        Kiwi.logger.info("Kiwi test: descended to $at")
    }

    private fun narrowLedgeDrop(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1
        val z = origin.z + 6

        world.fill(
            BlockPos(origin.x + 2, baseY + 2, z),
            BlockPos(origin.x + 8, baseY + 2, z),
            "minecraft:stone"
        )
        world.settle(6)

        val start = BlockPos(origin.x + 2, baseY + 3, z)
        val goal = BlockPos(origin.x + 12, baseY, z)
        world.teleport(start)

        world.expectSuccess(NavigateTask(GoalNear(goal, 1.5)), timeoutTicks = 20 * 45, what = "narrow ledge drop")

        val at = world.playerPos()
        check(at.y <= baseY + 1) { "should have dropped off the walkway, ended at $at" }
        check(at.x > origin.x + 8) { "should have crossed past the walkway end, ended at $at" }
        Kiwi.logger.info("Kiwi test: stepped off the narrow walkway to $at")
    }

    private fun tunnelThroughWall(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 32)
        val baseY = origin.y + 1
        val wallX = origin.x + 10

        world.fill(
            BlockPos(wallX, baseY, origin.z),
            BlockPos(wallX, baseY + 1, origin.z + 28),
            "minecraft:stone"
        )
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:iron_pickaxe")
        world.teleport(BlockPos(origin.x + 4, baseY, origin.z + 14))

        val goal = BlockPos(origin.x + 16, baseY, origin.z + 14)
        world.expectSuccess(
            NavigateTask(GoalNear(goal, 1.5)),
            timeoutTicks = 20 * 60,
            what = "tunnel through a wall"
        )

        val at = world.playerPos()
        check(at.x > wallX) { "should have crossed the wall, ended at $at" }

        val hole = (baseY..baseY + 1).count { y ->
            world.isAir(BlockPos(wallX, y, origin.z + 14)) ||
                world.isAir(BlockPos(wallX, y, origin.z + 13)) ||
                world.isAir(BlockPos(wallX, y, origin.z + 15))
        }
        check(hole > 0) { "the bot went around a 28 block wall instead of breaking through it" }
        Kiwi.logger.info("Kiwi test: tunnelled straight through the wall to $at")
    }

    private fun motionQuality(world: BotTestWorld) {
        val origin = world.arena(sizeX = 48, sizeZ = 16)
        val baseY = origin.y + 1
        val z = origin.z + 6

        val start = BlockPos(origin.x + 2, baseY, z)
        val goal = BlockPos(origin.x + 38, baseY, z)
        world.teleport(start)

        val trace = world.traceBot(NavigateTask(GoalNear(goal, 1.0)), timeoutTicks = 20 * 60)
        check(trace.result is TaskStatus.Success) { "straight run failed: ${trace.result}" }

        Kiwi.logger.info(
            "Kiwi test: 36 block run took ${trace.ticks} ticks, path ratio ${"%.2f".format(trace.wander)}, " +
                "${trace.reversals} direction reversals"
        )

        check(trace.reversals <= MAX_REVERSALS) {
            "movement oscillated: ${trace.reversals} direction reversals over ${trace.ticks} ticks"
        }
        check(trace.wander < 1.35) {
            "movement wandered: travelled ${"%.1f".format(trace.distanceTravelled)} blocks for " +
                "${"%.1f".format(trace.straightLineDistance)} blocks of progress"
        }
        check(trace.ticks < 20 * 20) { "36 block run took ${trace.ticks} ticks, far slower than walking" }
    }

    private fun searchPerformance(world: BotTestWorld) {
        val origin = world.arena(sizeX = 64, sizeZ = 64)
        val start = BlockPos(origin.x + 2, origin.y + 1, origin.z + 2)
        world.teleport(start)

        val (durationMs, status, nodes) = world.context.fromClient { _ ->
            val policy = Bot.pathPolicy()
            val goal = GoalNear(BlockPos(origin.x + 58, origin.y + 1, origin.z + 58), 1.0)
            val result = PathSearch(policy).search(start, (origin.y + 1).toDouble(), goal)
            Triple(result.durationMs, result.path.status, result.nodesExpanded)
        }

        Kiwi.logger.info(
            "Kiwi test: 80-block open search took ${"%.2f".format(durationMs)}ms, $nodes nodes, status=$status"
        )
        check(status == PathStatus.COMPLETE) { "open-field search should find a complete path, got $status" }
        check(durationMs < 250.0) { "open-field search took ${"%.1f".format(durationMs)}ms, too slow" }
    }
}
