package org.kvxd.kiwi.agent.pathing.calc

enum class MovementType(
    val canSprint: Boolean,
    val isSmoothable: Boolean
) {

    TRAVEL(true, true),
    JUMP(true, false),

    WATER_WALK(false, true),

    DROP(false, false),
    PILLAR(false, false)
}