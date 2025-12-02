package org.kvxd.baobab.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var maxFallHeight: Int = 3,
    var maxIterations: Int = 20_000,

    var renderPath: Boolean = true,
    var pathTimeoutMs: Long = 5000
)