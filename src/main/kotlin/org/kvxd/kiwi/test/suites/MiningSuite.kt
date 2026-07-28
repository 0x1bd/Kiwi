package org.kvxd.kiwi.test.suites

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.task.tasks.HarvestTask
import org.kvxd.kiwi.task.TaskStatus
import org.kvxd.kiwi.task.tasks.PickupTask
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check
import org.kvxd.kiwi.test.fromClient
import org.kvxd.kiwi.test.onClient

object MiningSuite {

    private const val TREE_HEIGHT = 5
    private const val MAX_MINING_SPREAD_SQ = 16.0

    fun run(world: BotTestWorld) {
        world.gamemode("survival")
        treeCommitment(world)
        stoneMining(world)
        buriedStone(world)
        dropBelowLedge(world)
        dropInSealedPocket(world)
        treeOnUnevenGround(world)
    }

    private fun treeCommitment(world: BotTestWorld) {
        val origin = world.arena(sizeX = 40, sizeZ = 24)
        val baseY = origin.y + 1

        val nearTree = BlockPos(origin.x + 8, baseY, origin.z + 6)
        val farTree = BlockPos(origin.x + 20, baseY, origin.z + 6)

        plantTree(world, nearTree)
        plantTree(world, farTree)
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:iron_axe")
        world.teleport(BlockPos(origin.x + 2, baseY, origin.z + 6))

        val logId = Ids.item("oak_log")
        world.expectSuccess(
            HarvestTask(intArrayOf(logId), amount = 4, label = "oak_log"),
            timeoutTicks = 20 * 120,
            what = "harvest 4 oak logs"
        )

        val collected = world.countItem(logId)
        check(collected >= 4) { "expected at least 4 oak logs, got $collected" }

        val nearRemaining = countLogs(world, nearTree)
        val farRemaining = countLogs(world, farTree)

        Kiwi.logger.info("Kiwi test: near tree has $nearRemaining logs left, far tree has $farRemaining")

        check(nearRemaining <= TREE_HEIGHT - 4) {
            "the bot should have felled the committed tree; $nearRemaining of $TREE_HEIGHT logs remain"
        }
        check(farRemaining == TREE_HEIGHT) {
            "the bot abandoned its tree and started on the other one ($farRemaining of $TREE_HEIGHT logs left)"
        }
    }

    private fun stoneMining(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1

        world.fill(
            BlockPos(origin.x + 6, baseY, origin.z + 4),
            BlockPos(origin.x + 8, baseY + 1, origin.z + 6),
            "minecraft:stone"
        )
        world.settle(6)

        world.clearInventory()
        world.give("minecraft:iron_pickaxe")
        world.teleport(BlockPos(origin.x + 2, baseY, origin.z + 5))

        val cobbleId = Ids.item("cobblestone")
        world.expectSuccess(
            HarvestTask(intArrayOf(cobbleId), amount = 3, label = "cobblestone"),
            timeoutTicks = 20 * 90,
            what = "harvest 3 cobblestone"
        )

        val collected = world.countItem(cobbleId)
        check(collected >= 3) { "expected at least 3 cobblestone, got $collected" }
        Kiwi.logger.info("Kiwi test: mined $collected cobblestone")
    }

    private fun buriedStone(world: BotTestWorld) {
        val sizeX = 24
        val sizeZ = 20
        val origin = world.arena(sizeX = sizeX, sizeZ = sizeZ)
        val baseY = origin.y

        val from = BlockPos(origin.x - 2, baseY, origin.z - 2)
        val to = BlockPos(origin.x + sizeX, baseY, origin.z + sizeZ)

        world.fill(from, to, "minecraft:dirt")
        world.fill(
            BlockPos(from.x, baseY - 2, from.z),
            BlockPos(to.x, baseY - 1, to.z),
            "minecraft:stone"
        )
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:iron_pickaxe")
        world.give("minecraft:iron_shovel")
        world.teleport(BlockPos(origin.x + 8, baseY + 1, origin.z + 8))

        val start = world.playerPos()
        val cobble = Ids.item("cobblestone")
        world.expectSuccess(
            HarvestTask(intArrayOf(cobble), amount = 3, label = "cobblestone"),
            timeoutTicks = 20 * 150,
            what = "dig down to buried stone"
        )

        val collected = world.countItem(cobble)
        check(collected >= 3) { "expected 3 cobblestone from below the surface, got $collected" }

        val mined = world.minedPositions(cobble)
        if (mined.size >= 2) {
            val spread = mined.maxOf { a -> mined.maxOf { b -> a.distSqr(b) } }
            check(spread <= MAX_MINING_SPREAD_SQ) {
                "mined blocks were scattered ${"%.1f".format(kotlin.math.sqrt(spread))} blocks apart; " +
                    "they should be neighbours"
            }
        }

        val end = world.playerPos()
        val travelled = kotlin.math.sqrt(
            ((end.x - start.x).toDouble() * (end.x - start.x) + (end.z - start.z).toDouble() * (end.z - start.z))
        )
        check(travelled < 12.0) {
            "the bot should have dug down near where it stood, but wandered ${"%.1f".format(travelled)} blocks"
        }
        Kiwi.logger.info(
            "Kiwi test: dug down to buried stone at $end after travelling ${"%.1f".format(travelled)} blocks"
        )
    }

    private fun dropBelowLedge(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1
        val pit = BlockPos(origin.x + 8, baseY - 1, origin.z + 6)

        world.setBlock(pit, "minecraft:air")
        world.settle(4)
        world.command("summon item ${pit.x + 0.5} ${pit.y + 0.2} ${pit.z + 0.5} {Item:{id:\"minecraft:oak_log\",count:1},PickupDelay:0s}")
        world.settle(8)

        world.clearInventory()
        world.teleport(BlockPos(origin.x + 4, baseY, origin.z + 6))

        val log = Ids.item("oak_log")
        world.expectSuccess(
            PickupTask(intArrayOf(log), targetCount = 1),
            timeoutTicks = 20 * 40,
            what = "collect a drop that fell a block below the ledge"
        )

        val collected = world.countItem(log)
        check(collected >= 1) { "expected the drop below the ledge to be collected, got $collected" }
        Kiwi.logger.info("Kiwi test: collected a drop resting a block below the walking surface")
    }

    private fun dropInSealedPocket(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1
        val pocket = BlockPos(origin.x + 8, baseY, origin.z + 6)

        world.fill(
            BlockPos(pocket.x - 1, baseY, pocket.z - 1),
            BlockPos(pocket.x + 1, baseY + 1, pocket.z + 1),
            "minecraft:stone"
        )
        world.setBlock(pocket, "minecraft:air")
        world.settle(4)
        world.command(
            "summon item ${pocket.x + 0.5} ${pocket.y + 0.2} ${pocket.z + 0.5} " +
                "{Item:{id:\"minecraft:oak_log\",count:1},PickupDelay:0s}"
        )
        world.settle(8)

        world.clearInventory()
        world.teleport(BlockPos(origin.x + 4, baseY, origin.z + 6))

        val log = Ids.item("oak_log")
        world.withBreakingDisabled {
            val (result, entered) = world.runBotAvoiding(
                PickupTask(intArrayOf(log), targetCount = 1),
                timeoutTicks = 20 * 40,
                forbidden = pocket
            )

            check(!entered) { "the bot squeezed into a one block gap at $pocket" }
            check(result is TaskStatus.Failure) {
                "a drop walled into a one block pocket is unreachable without mining, but got $result"
            }
            check(world.countItem(log) == 0) { "the bot should not have collected the walled-in drop" }
        }

        Kiwi.logger.info("Kiwi test: refused to squeeze into a one block pocket")
    }

    private fun treeOnUnevenGround(world: BotTestWorld) {
        val origin = world.arena(sizeX = 28, sizeZ = 20)
        val baseY = origin.y + 1
        val trunk = BlockPos(origin.x + 10, baseY, origin.z + 8)

        world.fill(trunk, BlockPos(trunk.x, trunk.y + 4, trunk.z), "minecraft:birch_log")
        world.fill(
            BlockPos(trunk.x - 2, baseY, trunk.z - 2),
            BlockPos(trunk.x + 2, baseY, trunk.z + 2),
            "minecraft:birch_leaves"
        )
        world.setBlock(trunk, "minecraft:birch_log")
        world.setBlock(BlockPos(trunk.x, baseY - 1, trunk.z), "minecraft:air")
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:iron_axe")
        world.teleport(BlockPos(origin.x + 3, baseY, origin.z + 8))

        val log = Ids.item("birch_log")
        world.expectSuccess(
            HarvestTask(intArrayOf(log), amount = 2, label = "birch_log"),
            timeoutTicks = 20 * 120,
            what = "harvest birch logs where the drops fall into the trunk hollow"
        )

        val collected = world.countItem(log)
        check(collected >= 2) { "expected 2 birch logs, got $collected" }
        Kiwi.logger.info("Kiwi test: harvested birch logs whose drops fell below the walking surface")
    }

    private fun plantTree(world: BotTestWorld, base: BlockPos) {
        world.fill(base, BlockPos(base.x, base.y + TREE_HEIGHT - 1, base.z), "minecraft:oak_log")
    }

    private fun countLogs(world: BotTestWorld, base: BlockPos): Int {
        var count = 0
        for (dy in 0 until TREE_HEIGHT) {
            if (world.blockAt(BlockPos(base.x, base.y + dy, base.z)) == "minecraft:oak_log") count++
        }
        return count
    }
}
