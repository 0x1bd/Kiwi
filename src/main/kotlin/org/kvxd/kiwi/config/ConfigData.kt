package org.kvxd.kiwi.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var maxFallHeight: Int = 3,
    var maxIterations: Int = 20_000,

    var horizontalDeviationThreshold: Double = 4.0,
    var verticalDeviationThreshold: Double = 2.5,

    var renderPath: Boolean = true,
    var debugMode: Boolean = false,

    /**
     * If a position submitted to a goal needs to be an exact coordinate
     * if false, the block above is also accepted if the targeted block is solid.
     */
    var strictPosition: Boolean = false,

    var freelook: Boolean = true,

    var allowPillar: Boolean = true,
    var allowWater: Boolean = false
)