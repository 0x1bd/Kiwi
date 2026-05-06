package org.kvxd.kiwi.agent.pathing.move

import kotlin.math.sqrt

object MovementCosts {
    const val FLAT = 1.0
    val DIAGONAL: Double = sqrt(2.0)

    const val STEP_UP = 2.8
    const val WATER = 4.0
    val WATER_DIAGONAL: Double = WATER * sqrt(2.0)

    const val DROP_BASE = 3.5
    const val DROP_PER_BLOCK = 1.1
    const val WATER_DROP_PER_BLOCK = 0.6

    const val PILLAR = 18.0

    const val MINING_BASE = 24.0
    const val MINING_TIME_MULTIPLIER = 12.0
    const val MINING_PER_BLOCK = 5.0
}