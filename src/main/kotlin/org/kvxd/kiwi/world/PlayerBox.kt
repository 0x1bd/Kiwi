package org.kvxd.kiwi.world

import net.minecraft.world.phys.AABB

object PlayerBox {

    const val WIDTH = 0.6
    const val HALF_WIDTH = WIDTH / 2.0
    const val HEIGHT = 1.8
    const val SNEAK_HEIGHT = 1.5
    const val EYE_HEIGHT = 1.62
    const val STEP_HEIGHT = 0.6
    const val JUMP_HEIGHT = 1.25

    fun at(x: Double, feetY: Double, z: Double, height: Double = HEIGHT): AABB =
        AABB(x - HALF_WIDTH, feetY, z - HALF_WIDTH, x + HALF_WIDTH, feetY + height, z + HALF_WIDTH)

    fun centeredOn(cellX: Int, feetY: Double, cellZ: Int, height: Double = HEIGHT): AABB =
        at(cellX + 0.5, feetY, cellZ + 0.5, height)
}
