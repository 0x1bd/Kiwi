package org.kvxd.kiwi.agent.pathing.calc

import org.kvxd.kiwi.agent.pathing.execute.MovementExecutor
import org.kvxd.kiwi.agent.pathing.execute.types.DropExecutor
import org.kvxd.kiwi.agent.pathing.execute.types.PillarExecutor
import org.kvxd.kiwi.agent.pathing.execute.types.StandardExecutor

enum class MovementType(
    val canSprint: Boolean,
    val isSmoothable: Boolean,
    val executor: MovementExecutor
) {

    TRAVEL(true, true, StandardExecutor),
    JUMP(true, false, StandardExecutor),

    WATER_WALK(false, true, StandardExecutor),

    DROP(false, false, DropExecutor),
    PILLAR(false, false, PillarExecutor)
}