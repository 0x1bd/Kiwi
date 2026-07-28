package org.kvxd.kiwi.test.suites

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.task.tasks.AcquireItemTask
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check

object PlanningSuite {

    fun run(world: BotTestWorld) {
        world.gamemode("survival")
        prefersNearbyTrees(world)
        planksFromTrees(world)
        craftingTableFromScratch(world)
        stonePickaxeFromScratch(world)
    }

    private fun prefersNearbyTrees(world: BotTestWorld) {
        val origin = world.arena(sizeX = 48, sizeZ = 24)
        val baseY = origin.y + 1

        plantTree(world, BlockPos(origin.x + 8, baseY, origin.z + 8))

        world.fill(
            BlockPos(origin.x + 40, baseY, origin.z + 16),
            BlockPos(origin.x + 42, baseY + 1, origin.z + 18),
            "minecraft:oak_planks"
        )
        world.settle(8)

        world.clearInventory()
        val start = BlockPos(origin.x + 4, baseY, origin.z + 6)
        world.teleport(start)

        val planks = Ids.item("oak_planks")
        world.expectSuccess(
            AcquireItemTask(intArrayOf(planks), amount = 4, label = "oak_planks"),
            timeoutTicks = 20 * 200,
            what = "prefer the nearby tree over distant plank blocks"
        )

        val end = world.playerPos()
        val travelled = kotlin.math.sqrt(
            ((end.x - start.x).toDouble() * (end.x - start.x) + (end.z - start.z).toDouble() * (end.z - start.z))
        )
        check(travelled < 24.0) {
            "the bot walked ${"%.1f".format(travelled)} blocks; it should have used the tree next to it"
        }
        Kiwi.logger.info(
            "Kiwi test: chose the nearby tree over distant planks, travelling ${"%.1f".format(travelled)} blocks"
        )
    }

    private fun planksFromTrees(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 24)
        val baseY = origin.y + 1

        plantTree(world, BlockPos(origin.x + 10, baseY, origin.z + 8))
        plantTree(world, BlockPos(origin.x + 14, baseY, origin.z + 14))
        world.settle(8)

        world.clearInventory()
        world.teleport(BlockPos(origin.x + 3, baseY, origin.z + 3))

        val planks = Ids.item("oak_planks")
        world.expectSuccess(
            AcquireItemTask(intArrayOf(planks), amount = 4, label = "oak_planks"),
            timeoutTicks = 20 * 180,
            what = "acquire 4 oak planks from standing trees"
        )

        val count = world.countItem(planks)
        check(count >= 4) { "expected at least 4 oak planks, got $count" }
        Kiwi.logger.info("Kiwi test: planner produced $count oak planks from raw trees")
    }

    private fun craftingTableFromScratch(world: BotTestWorld) {
        val origin = world.arena(sizeX = 32, sizeZ = 24)
        val baseY = origin.y + 1

        plantTree(world, BlockPos(origin.x + 9, baseY, origin.z + 9))
        world.settle(8)

        world.clearInventory()
        world.teleport(BlockPos(origin.x + 3, baseY, origin.z + 3))

        val table = Ids.item("crafting_table")
        world.expectSuccess(
            AcquireItemTask(intArrayOf(table), amount = 1, label = "crafting_table"),
            timeoutTicks = 20 * 240,
            what = "acquire a crafting table from scratch"
        )

        val count = world.countItem(table)
        check(count >= 1) { "expected a crafting table, got $count" }
        Kiwi.logger.info("Kiwi test: planner chained logs -> planks -> crafting table")
    }

    private fun stonePickaxeFromScratch(world: BotTestWorld) {
        val origin = world.arena(sizeX = 40, sizeZ = 24)
        val baseY = origin.y + 1

        plantTree(world, BlockPos(origin.x + 9, baseY, origin.z + 8))
        plantTree(world, BlockPos(origin.x + 13, baseY, origin.z + 12))
        world.fill(
            BlockPos(origin.x + 20, baseY, origin.z + 6),
            BlockPos(origin.x + 24, baseY + 2, origin.z + 10),
            "minecraft:stone"
        )
        world.settle(8)

        world.clearInventory()
        world.teleport(BlockPos(origin.x + 3, baseY, origin.z + 4))

        val pickaxe = Ids.item("stone_pickaxe")
        world.expectSuccess(
            AcquireItemTask(intArrayOf(pickaxe), amount = 1, label = "stone_pickaxe"),
            timeoutTicks = 20 * 420,
            what = "acquire a stone pickaxe from scratch"
        )

        val count = world.countItem(pickaxe)
        check(count >= 1) { "expected a stone pickaxe, got $count" }
        Kiwi.logger.info("Kiwi test: planner chained trees and stone into a stone pickaxe")
    }

    private fun plantTree(world: BotTestWorld, base: BlockPos) {
        world.fill(base, BlockPos(base.x, base.y + 4, base.z), "minecraft:oak_log")
    }
}
