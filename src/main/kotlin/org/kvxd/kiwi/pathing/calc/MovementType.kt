package org.kvxd.kiwi.pathing.calc

import org.kvxd.kiwi.pathing.execute.MovementExecutor
import org.kvxd.kiwi.pathing.execute.types.DropExecutor
import org.kvxd.kiwi.pathing.execute.types.MineExecutor
import org.kvxd.kiwi.pathing.execute.types.PillarExecutor
import org.kvxd.kiwi.pathing.execute.types.StandardExecutor

enum class MovementType(
    val canSprint: Boolean,
    val isSmoothable: Boolean,
    val executor: MovementExecutor
) {

    TRAVEL(true, true, StandardExecutor),
    JUMP(true, false, StandardExecutor),

    DROP(false, false, DropExecutor),
    PILLAR(false, false, PillarExecutor),
    MINE(false, false, MineExecutor)
}