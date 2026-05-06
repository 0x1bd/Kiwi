package org.kvxd.kiwi.config

import org.kvxd.kiwi.config.entries.boolean
import org.kvxd.kiwi.config.entries.double
import org.kvxd.kiwi.config.entries.int
import org.kvxd.kiwi.config.entries.stringList
import org.kvxd.kiwi.util.resolveBlockId

object ConfigData {

    var maxFallHeight by int(
        "maxFallHeight",
        "Max blocks Kiwi is allowed to fall from",
        3
    )

    var horizontalDeviationThreshold by double(
        "horizontalDeviationThreshold",
        "Allowed horizontal deviation",
        4.0
    )

    var renderPath by boolean(
        "renderPath",
        "Whether to visually render the computed path",
        true
    )

    var debugMode by boolean(
        "debugMode",
        "Enables debug information",
        false
    )

    var strictPosition by boolean(
        "strictPosition",
        "If true, the goal position must match exactly; otherwise allows the block above if solid",
        false
    )

    var freelook by boolean(
        "freelook",
        "Whether freelook is enabled",
        true
    )

    var allowPillar by boolean(
        "allowPillar",
        "Allow placing blocks upward to climb",
        true
    )

    var allowWater by boolean(
        "allowWater",
        "Allow movement through or interaction with water",
        false
    )

    var allowBreak by boolean(
        "allowBreak",
        "Allow breaking blocks",
        true
    )

    var renderAgentOverlay by boolean(
        "renderAgentOverlay",
        "Whether to show the agent HUD overlay",
        true
    )

    var allowedBuildBlocks by stringList(
        "allowedBuildBlocks",
        "Block IDs allowed for building/pillaring",
        listOf("dirt", "cobblestone", "netherrack", "andesite", "diorite", "granite", "deepslate", "cobbled_deepslate", "tuff", "stone")
    )

    val allowedBuildBlockIds: Set<String>
        get() = allowedBuildBlocks.toSet()

    val allowedBuildBlockTypes
        get() = allowedBuildBlockIds.mapNotNull { resolveBlockId(it) }.toSet()

    var safeToMineBlocks by stringList(
        "safeToMineBlocks",
        "Block IDs that are safe to mine when they block the way",
        listOf("oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "pale_oak_leaves", "mangrove_leaves", "cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "tall_grass", "fern", "dead_bush", "vine", "glow_lichen")
    )

    val safeToMineBlockIds: Set<String>
        get() = safeToMineBlocks.toSet()

    val safeToMineBlockTypes
        get() = safeToMineBlockIds.mapNotNull { resolveBlockId(it) }.toSet()

    var blockScanRadius by int(
        "blockScanRadius",
        "Radius (in blocks) to scan for harvestable blocks",
        60
    )

    var dropScanRadius by int(
        "dropScanRadius",
        "Radius (in blocks) to scan for dropped items",
        32
    )

    var collectTimeoutTicks by int(
        "collectTimeoutTicks",
        "Ticks to wait near a drop before giving up",
        30
    )

    var collectMaxAttempts by int(
        "collectMaxAttempts",
        "How many times to retry collecting an item before failing the action",
        3
    )

    var collectFineTuneTicks by int(
        "collectFineTuneTicks",
        "Maximum ticks to spend on fine positioning after navigation",
        40
    )

    var craftTableScanRadius by int(
        "craftTableScanRadius",
        "Radius (in blocks) to scan for existing crafting tables",
        8
    )

    var stuckThresholdTicks by int(
        "stuckThresholdTicks",
        "Ticks of no movement before agent declares itself stuck",
        80
    )

    var agentMaxPlanSteps by int(
        "agentMaxPlanSteps",
        "Maximum planner decisions before the agent is considered stuck",
        256
    )

    var agentMaxFailures by int(
        "agentMaxFailures",
        "Maximum recoverable agent action failures before stopping",
        12
    )
}
