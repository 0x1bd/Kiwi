package org.kvxd.kiwi.pathing.calc

enum class MovementType(val canSprint: Boolean) {
    WALK(true),

    JUMP(false),
    DROP(false),
    DIAGONAL(true)
}