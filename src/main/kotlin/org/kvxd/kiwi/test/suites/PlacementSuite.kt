package org.kvxd.kiwi.test.suites

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.knowledge.Ids
import org.kvxd.kiwi.task.tasks.PlaceBlockTask
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check
import org.kvxd.kiwi.test.fromClient
import org.kvxd.kiwi.test.onClient

object PlacementSuite {

    fun run(world: BotTestWorld) {
        world.gamemode("survival")
        onGroundCover(world, "minecraft:snow[layers=3]", "snow")
        onGroundCover(world, "minecraft:leaf_litter", "leaf litter")
        onSlabs(world)
        staysAtFootLevel(world)
        reusesDistantTable(world)
    }

    private fun onGroundCover(world: BotTestWorld, cover: String, label: String) {
        val origin = world.arena(sizeX = 16, sizeZ = 16)
        val baseY = origin.y + 1

        world.fill(
            BlockPos(origin.x, baseY, origin.z),
            BlockPos(origin.x + 10, baseY, origin.z + 10),
            cover
        )
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:crafting_table")
        world.teleport(BlockPos(origin.x + 5, baseY + 1, origin.z + 5))

        val table = Ids.item("crafting_table")
        world.expectSuccess(
            PlaceBlockTask(table),
            timeoutTicks = 20 * 40,
            what = "place a crafting table on ground covered in $label"
        )

        check(tableNearby(world, world.playerPos())) { "no crafting table was placed on $label" }
        Kiwi.logger.info("Kiwi test: placed a crafting table over $label")
    }

    private fun onSlabs(world: BotTestWorld) {
        val origin = world.arena(sizeX = 16, sizeZ = 16)
        val baseY = origin.y + 1

        world.fill(
            BlockPos(origin.x, baseY, origin.z),
            BlockPos(origin.x + 10, baseY, origin.z + 10),
            "minecraft:stone_slab[type=bottom]"
        )
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:crafting_table")
        world.teleport(BlockPos(origin.x + 5, baseY + 1, origin.z + 5))

        val table = Ids.item("crafting_table")
        world.expectSuccess(
            PlaceBlockTask(table),
            timeoutTicks = 20 * 40,
            what = "place a crafting table on a slab floor"
        )

        check(tableNearby(world, world.playerPos())) { "no crafting table was placed on the slab floor" }
        Kiwi.logger.info("Kiwi test: placed a crafting table on a slab floor")
    }

    private fun staysAtFootLevel(world: BotTestWorld) {
        val origin = world.arena(sizeX = 16, sizeZ = 16)
        val baseY = origin.y + 1

        world.fill(
            BlockPos(origin.x + 4, baseY, origin.z + 4),
            BlockPos(origin.x + 8, baseY, origin.z + 8),
            "minecraft:leaf_litter"
        )
        world.settle(8)

        world.clearInventory()
        world.give("minecraft:crafting_table")
        val standing = BlockPos(origin.x + 6, baseY, origin.z + 6)
        world.teleport(standing)

        val table = Ids.item("crafting_table")
        world.expectSuccess(
            PlaceBlockTask(table),
            timeoutTicks = 20 * 40,
            what = "place a crafting table amongst leaf litter"
        )

        val placed = tablePosition(world, world.playerPos())
        check(placed != null) { "no crafting table was placed" }
        check(placed!!.y <= world.playerPos().y) {
            "the table was placed at head level ($placed) while standing at ${world.playerPos()}"
        }
        Kiwi.logger.info("Kiwi test: placed the crafting table at foot level ($placed)")
    }

    private fun reusesDistantTable(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 16)
        val baseY = origin.y + 1
        val table = BlockPos(origin.x + 14, baseY, origin.z + 6)

        world.setBlock(table, "minecraft:crafting_table")
        world.settle(6)

        world.clearInventory()
        world.give("minecraft:oak_planks", 3)
        world.give("minecraft:stick", 2)
        world.teleport(BlockPos(origin.x + 2, baseY, origin.z + 6))

        val recipe = org.kvxd.kiwi.knowledge.Knowledge.craftsFor(Ids.item("wooden_pickaxe")).first()
        world.expectSuccess(
            org.kvxd.kiwi.task.tasks.CraftTask(recipe),
            timeoutTicks = 20 * 60,
            what = "walk to an existing crafting table 12 blocks away"
        )

        check(world.countItem(Ids.item("wooden_pickaxe")) >= 1) { "the pickaxe was not crafted" }
        check(world.blockAt(table) == "minecraft:crafting_table") { "the existing table should still be there" }
        Kiwi.logger.info("Kiwi test: reused a crafting table 12 blocks away instead of making another")
    }

    private fun tablePosition(world: BotTestWorld, origin: BlockPos): BlockPos? = world.context.fromClient { client ->
        val level = client.level!!
        var found: BlockPos? = null
        for (dx in -6..6) {
            for (dy in -3..3) {
                for (dz in -6..6) {
                    val pos = BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)
                    if (level.getBlockState(pos).`is`(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
                        found = pos
                    }
                }
            }
        }
        found
    }

    private fun tableNearby(world: BotTestWorld, origin: BlockPos): Boolean = world.context.fromClient { client ->
        val level = client.level!!
        var found = false
        for (dx in -6..6) {
            for (dy in -3..3) {
                for (dz in -6..6) {
                    val pos = BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)
                    if (level.getBlockState(pos).`is`(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
                        found = true
                    }
                }
            }
        }
        found
    }
}
