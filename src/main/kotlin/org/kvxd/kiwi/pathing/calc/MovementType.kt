package org.kvxd.kiwi.pathing.calc

import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.pathing.execute.types.DropExecutor
import org.kvxd.kiwi.pathing.execute.types.PillarExecutor
import org.kvxd.kiwi.pathing.execute.types.StandardExecutor

enum class MovementType(
    val canSprint: Boolean,
    val executor: MovementExecutor
) {
    WALK(true, StandardExecutor),
    DIAGONAL(true, StandardExecutor),
    JUMP(true, StandardExecutor),
    DROP(false, DropExecutor),
    PILLAR(false, PillarExecutor)
}