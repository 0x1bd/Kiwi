package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.client.gui.screens.inventory.FurnaceScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.FurnaceMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.*
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.input.InputOverride
import org.kvxd.kiwi.agent.pathing.cache.CollisionCache
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.canInteractWithBlock
import org.kvxd.kiwi.client
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.isCrosshairOnBlock
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import org.kvxd.kiwi.util.InventoryUtil
import org.kvxd.kiwi.util.registryPath
import org.kvxd.kiwi.util.math.RotationUtils
import kotlinx.coroutines.delay
import net.minecraft.world.inventory.ContainerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

suspend fun AgentRuntime.craftItem(recipe: Recipe) {
    phase = AgentPhase.CRAFTING
    InputOverride.update { attack = false }

    val needTable = recipe.source == ItemSource.CRAFTING_TABLE

    if (needTable) {
        val tablePos = findOrPlaceCraftingTable()
        openCraftingTable(tablePos)
    } else {
        client.setScreen(InventoryScreen(player))
        delay(100.milliseconds)
    }

    waitForRightScreen(needTable)
    placeCraftingIngredients(recipe)
    waitForResult()
    quickMoveResult()
    closeScreen()
}

suspend fun AgentRuntime.smeltItem(recipe: Recipe) {
    phase = AgentPhase.SMELTING
    InputOverride.update { attack = false }

    val furnacePos = findOrPlaceFurnace()
    openFurnace(furnacePos)
    waitForScreen<FurnaceMenu>()

    val menu = player.containerMenu as FurnaceMenu
    val gameMode = client.gameMode!!

    val fuelIds = listOf("coal", "charcoal", "coal_block")
    val ingredientIds = recipe.ingredients.firstOrNull()?.itemIds ?: throw AgentFailure("No ingredient for smelting")
    val invSlot = findInvSlot(menu, ingredientIds, 3..38)
    if (invSlot == -1) throw AgentFailure("Missing ingredient for smelting")
    gameMode.handleContainerInput(menu.containerId, invSlot, 0, ContainerInput.PICKUP, player)
    gameMode.handleContainerInput(menu.containerId, 0, 1, ContainerInput.PICKUP, player)

    val fuelSlot = findInvSlot(menu, fuelIds, 3..38)
    if (fuelSlot == -1) throw AgentFailure("No fuel for smelting")
    gameMode.handleContainerInput(menu.containerId, fuelSlot, 0, ContainerInput.PICKUP, player)
    gameMode.handleContainerInput(menu.containerId, 1, 1, ContainerInput.PICKUP, player)

    var ticks = 0
    while (!menu.getSlot(2).hasItem() && ticks < 200) {
        delay(50.milliseconds)
        ticks++
    }
    if (ticks >= 200) throw AgentFailure("Smelting timed out")

    gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.QUICK_MOVE, player)
    delay(250.milliseconds)
    closeScreen()
}

private suspend fun AgentRuntime.findOrPlaceCraftingTable(): BlockPos {
    val remembered = agent.findRememberedBlock(Blocks.CRAFTING_TABLE)
    if (remembered != null) return remembered

    val nearby = ScanUtil.findNearestByType(radius = ConfigData.craftTableScanRadius, blockType = Blocks.CRAFTING_TABLE)
    if (nearby != null) {
        agent.rememberBlock(Blocks.CRAFTING_TABLE, nearby.pos)
        return nearby.pos
    }

    return placeWorkstation(
        item = Items.CRAFTING_TABLE,
        block = Blocks.CRAFTING_TABLE,
        memoryBlock = Blocks.CRAFTING_TABLE,
        missingMessage = "No crafting table in inventory"
    )
}

private suspend fun AgentRuntime.placeWorkstation(
    item: Item,
    block: Block,
    memoryBlock: Block,
    missingMessage: String
): BlockPos {
    if (!InventoryUtil.ensureInHotbar(item)) throw AgentFailure(missingMessage)

    val target = findPlacementTarget(player.blockPosition())
        ?: throw AgentFailure("No place for ${block.registryPath}")

    if (target.standPos != player.blockPosition()) {
        walkTo(target.standPos, 0.75)
    }

    if (!InventoryUtil.ensureInHotbar(item)) throw AgentFailure(missingMessage)
    placeBlock(target, block)
    agent.rememberBlock(memoryBlock, target.placePos)
    return target.placePos
}

private data class PlacementTarget(
    val placePos: BlockPos,
    val supportPos: BlockPos,
    val standPos: BlockPos,
    val hitVec: Vec3
)

private fun findPlacementTarget(origin: BlockPos): PlacementTarget? {
    val occupiedByPlayer = setOf(player.blockPosition(), player.blockPosition().above())

    for (radius in 1..3) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (max(abs(dx), abs(dz)) != radius) continue

                val base = BlockPos(origin.x + dx, origin.y, origin.z + dz)
                for (dy in listOf(0, -1, 1)) {
                    val placePos = BlockPos(base.x, base.y + dy, base.z)
                    if (!canPlaceAt(placePos, occupiedByPlayer)) continue

                    val standPos = findPlacementStand(placePos) ?: continue
                    val supportPos = placePos.below()
                    val hitVec = Vec3(supportPos.x + 0.5, supportPos.y + 1.0, supportPos.z + 0.5)
                    return PlacementTarget(placePos, supportPos, standPos, hitVec)
                }
            }
        }
    }

    return null
}

private fun canPlaceAt(placePos: BlockPos, occupiedByPlayer: Set<BlockPos>): Boolean {
    if (placePos in occupiedByPlayer) return false
    if (!level.getBlockState(placePos).isAir) return false
    return CollisionCache.isSolid(placePos.below())
}

private fun findPlacementStand(placePos: BlockPos): BlockPos? {
    val playerPos = player.blockPosition()
    val candidates = buildList {
        add(playerPos)
        for (dir in Direction.Plane.HORIZONTAL) {
            add(placePos.relative(dir))
        }
    }

    return candidates
        .distinct()
        .filter { it != placePos && CollisionCache.isWalkable(it) }
        .minByOrNull { playerPos.distSqr(it) }
}

private suspend fun placeBlock(target: PlacementTarget, block: Block) {
    InputOverride.update { use = false }

    val rots = RotationUtils.getLookRotations(target.hitVec)
    RotationManager.setTarget(rots.x, rots.y)

    var aimTicks = 0
    while (!RotationUtils.isLookingAt(target.hitVec, 0.6) && aimTicks < 12) {
        delay(50.milliseconds)
        aimTicks++
    }

    val hit = BlockHitResult(target.hitVec, Direction.UP, target.supportPos, false)
    repeat(8) {
        val result = client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        if (result?.consumesAction() == true) {
            player.swing(InteractionHand.MAIN_HAND)
        }

        delay(100.milliseconds)
        CollisionCache.invalidate(target.placePos)
        if (level.getBlockState(target.placePos).`is`(block)) return
    }

    throw AgentFailure("Could not place ${block.registryPath}")
}

private suspend fun AgentRuntime.openCraftingTable(pos: BlockPos) {
    if (!canInteractWithBlock(pos)) walkTo(findAdjacent(pos) ?: pos, 0.85)
    lookAtBlock(pos)
    var opened = false
    repeat(15) {
        InputOverride.update {
            use = isCrosshairOnBlock(pos) && canInteractWithBlock(pos)
        }
        delay(50.milliseconds)
        if (client.screen is CraftingScreen) {
            InputOverride.update { use = false }
            opened = true
            return@repeat
        }
    }
    if (!opened) throw AgentFailure("Could not open crafting table")
}

private suspend fun AgentRuntime.openFurnace(pos: BlockPos) {
    if (!canInteractWithBlock(pos)) walkTo(findAdjacent(pos) ?: pos, 0.85)
    lookAtBlock(pos)
    var opened = false
    repeat(20) {
        InputOverride.update {
            use = isCrosshairOnBlock(pos)
        }
        delay(50.milliseconds)
        if (client.screen is FurnaceScreen) {
            InputOverride.update { use = false }
            opened = true
            return@repeat
        }
    }
    if (!opened) throw AgentFailure("Could not open furnace")
}

private suspend fun AgentRuntime.findOrPlaceFurnace(): BlockPos {
    val existing = agent.findRememberedBlock(Blocks.FURNACE) ?: ScanUtil.findNearestByType(radius = ConfigData.craftTableScanRadius, blockType = Blocks.FURNACE)?.pos
    if (existing != null) return existing
    return placeWorkstation(
        item = Items.FURNACE,
        block = Blocks.FURNACE,
        memoryBlock = Blocks.FURNACE,
        missingMessage = "No furnace in inventory"
    )
}

private suspend fun AgentRuntime.waitForRightScreen(needsTable: Boolean) {
    var ticks = 0
    while (ticks < 50) {
        val menu = player.containerMenu
        if ((!needsTable && menu is InventoryMenu) || (needsTable && menu is CraftingMenu)) return
        delay(50.milliseconds)
        ticks++
    }
    throw AgentFailure("Crafting GUI did not open in time")
}

private suspend inline fun <reified T> waitForScreen() {
    var ticks = 0
    while (ticks < 60) {
        if (player.containerMenu is T) return
        delay(50.milliseconds)
        ticks++
    }
    throw AgentFailure("Screen did not open")
}

private suspend fun AgentRuntime.placeCraftingIngredients(recipe: Recipe) {
    val menu = player.containerMenu
    val gameMode = client.gameMode!!
    val gridWidth = if (recipe.source == ItemSource.HAND_CRAFT) 2 else 3

    val slotIngredientPairs = if (recipe.width > 0 && recipe.height > 0) {
        recipe.slots.mapIndexedNotNull { index, ingredient ->
            if (ingredient == null) null
            else {
                val row = index / recipe.width
                val col = index % recipe.width
                val menuSlot = 1 + row * gridWidth + col
                menuSlot to ingredient
            }
        }
    } else {
        recipe.ingredients.mapIndexedNotNull { index, ingredient ->
            if (index >= gridWidth * gridWidth) null
            else {
                val menuSlot = 1 + index
                menuSlot to ingredient
            }
        }
    }

    for ((gridSlot, ingredient) in slotIngredientPairs) {
        val invSlot = findInvSlot(menu, ingredient.itemIds, invRange(recipe.source))
        if (invSlot == -1) throw AgentFailure("Missing ${ingredient.displayName} for crafting")

        gameMode.handleContainerInput(menu.containerId, invSlot, 0, ContainerInput.PICKUP, player)
        delay(50.milliseconds)

        gameMode.handleContainerInput(menu.containerId, gridSlot, 1, ContainerInput.PICKUP, player)
        delay(50.milliseconds)

        gameMode.handleContainerInput(menu.containerId, invSlot, 0, ContainerInput.PICKUP, player)
        delay(50.milliseconds)
    }

    if (!menu.carried.isEmpty) {
        val emptySlot = findEmptySlot(menu, invRange(recipe.source))
        if (emptySlot != -1) {
            gameMode.handleContainerInput(menu.containerId, emptySlot, 0, ContainerInput.PICKUP, player)
            delay(50.milliseconds)
        }
    }
}

private fun findEmptySlot(menu: net.minecraft.world.inventory.AbstractContainerMenu, range: IntRange): Int {
    for (i in range) {
        if (menu.getSlot(i).item.isEmpty) return i
    }
    return -1
}

private fun invRange(source: ItemSource): IntRange = when (source) {
    ItemSource.HAND_CRAFT -> 9..45
    ItemSource.CRAFTING_TABLE -> 10..45
    else -> 0..0
}

private fun findInvSlot(menu: net.minecraft.world.inventory.AbstractContainerMenu, itemIds: List<String>, range: IntRange): Int {
    for (i in range) {
        val stack = menu.getSlot(i).item
        if (stack.isEmpty) continue
        val id = BuiltInRegistries.ITEM.getKey(stack.item).path
        if (id in itemIds) return i
    }
    return -1
}

private suspend fun AgentRuntime.waitForResult() {
    var ticks = 0
    while (!player.containerMenu.getSlot(0).hasItem() && ticks < 40) {
        delay(50.milliseconds)
        ticks++
    }
    if (ticks >= 40) throw AgentFailure("Crafting did not produce result")
}

private suspend fun AgentRuntime.quickMoveResult() {
    val menu = player.containerMenu
    client.gameMode?.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player)
    delay(100.milliseconds)
}

private suspend fun AgentRuntime.closeScreen() {
    if (client.screen != null) player.closeContainer()
    delay(100.milliseconds)
}

private fun findAdjacent(pos: BlockPos): BlockPos? {
    for (dir in Direction.Plane.HORIZONTAL) {
        val adj = pos.relative(dir)
        if (CollisionCache.isWalkable(adj)) return adj
    }
    return null
}
