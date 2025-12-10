package org.kvxd.kiwi.config

import org.kvxd.kiwi.config.entries.boolean
import org.kvxd.kiwi.config.entries.double
import org.kvxd.kiwi.config.entries.int

object ConfigData {

    var maxFallHeight by int(
        "maxFallHeight",
        "Max blocks Kiwi is allowed to fall from",
        3
    )

    var maxIterations by int(
        "maxIterations",
        "Maximum iterations the pathfinding algorithm can perform",
        20_000
    )

    var horizontalDeviationThreshold by double(
        "horizontalDeviationThreshold",
        "Allowed horizontal deviation",
        4.0
    )

    var verticalDeviationThreshold by double(
        "verticalDeviationThreshold",
        "Allowed vertical deviation",
        2.5
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
}
