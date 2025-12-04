package org.kvxd.kiwi.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var maxFallHeight: Int = 3,
    var maxIterations: Int = 20_000,

    var backtrackThreshold: Double = 1.5,

    var horizontalDeviationThreshold: Double = 4.0,
    var verticalDeviationThreshold: Double = 2.5,

    var renderPath: Boolean = true,
    var debugMode: Boolean = false,

    var freelook: Boolean = true
)