package org.kvxd.baobab.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var allowBreaking: Boolean = true,
    var maxFallHeight: Int = 3,
    var maxIterations: Int = 20_000,
    var stepHeight: Double = 0.6,
    var movementCostMult: Double = 1.0,

    var debugRender: Boolean = true,
    var pathTimeoutMs: Long = 5000,

    var searchRadius: Int = 100
)