package org.kvxd.kiwi.agent.pathing.execute

import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.execute.types.DropExecutor
import org.kvxd.kiwi.agent.pathing.execute.types.PillarExecutor
import org.kvxd.kiwi.agent.pathing.execute.types.StandardExecutor

object MovementExecutorRegistry {

    fun executorFor(type: MovementType): MovementExecutor = when (type) {
        MovementType.TRAVEL,
        MovementType.JUMP,
        MovementType.WATER_WALK -> StandardExecutor
        MovementType.DROP -> DropExecutor
        MovementType.PILLAR -> PillarExecutor
    }
}