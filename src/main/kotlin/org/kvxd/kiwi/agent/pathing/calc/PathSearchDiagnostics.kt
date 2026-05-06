package org.kvxd.kiwi.agent.pathing.calc

import org.kvxd.kiwi.agent.capability.MovementCapability

object PathSearchDiagnostics {

    private val missingCapabilities = ThreadLocal.withInitial { mutableSetOf<MovementCapability>() }

    fun reset() {
        missingCapabilities.get().clear()
    }

    fun require(capability: MovementCapability) {
        missingCapabilities.get().add(capability)
    }

    fun missingCapabilities(): Set<MovementCapability> = missingCapabilities.get().toSet()
}