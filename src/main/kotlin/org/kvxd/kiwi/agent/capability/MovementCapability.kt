package org.kvxd.kiwi.agent.capability

import org.kvxd.kiwi.config.ConfigData

enum class MovementCapability(val label: String) {
    WATER_TRAVERSAL("water traversal")
}

object MovementCapabilities {

    fun isEnabled(capability: MovementCapability): Boolean {
        return when (capability) {
            MovementCapability.WATER_TRAVERSAL -> ConfigData.allowWater
        }
    }

    fun require(capability: MovementCapability): Boolean = isEnabled(capability)
}