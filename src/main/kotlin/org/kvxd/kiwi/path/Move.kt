package org.kvxd.kiwi.path

enum class MoveKind(
    val allowsSprint: Boolean,
    val smoothable: Boolean
) {
    WALK(true, true),
    JUMP(false, false),
    FALL(false, false),
    SWIM(false, true),
    CLIMB_UP(false, false),
    CLIMB_DOWN(false, false),
    PILLAR(false, false),
    DESCEND(false, false)
}

object MoveCosts {
    const val WALK = 1.0
    const val DIAGONAL = 1.4142135623730951
    const val STEP_UP = 0.35
    const val STEP_DOWN = 0.15
    const val JUMP_BASE = 1.6
    const val FALL_PER_BLOCK = 0.55
    const val SWIM = 3.4
    const val CLIMB = 2.4
    const val PILLAR = 4.0
    const val DESCEND = 2.0
    const val BREAK_OVERHEAD = 0.9
    const val BREAK_TICK_SCALE = 0.22
    const val BREAK_ESCALATION = 0.45
    const val MAX_PATH_BREAKS = 24
}

val NO_BREAKS: LongArray = LongArray(0)

const val NO_PLACE: Long = Long.MIN_VALUE
