package org.kvxd.kiwi.world

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape

enum class ShapeKind {
    EMPTY,
    FULL_CUBE,
    PARTIAL
}

enum class FluidKind {
    NONE,
    WATER,
    LAVA
}

enum class Hazard {
    NONE,
    CONTACT_DAMAGE,
    SINKING,
    LAVA,
    FALL_THROUGH
}

class BlockProfile(
    val state: BlockState,
    val shapeKind: ShapeKind,
    val shape: VoxelShape,
    val footprintSpans: DoubleArray,
    val supportTop: Double,
    val fluid: FluidKind,
    val fluidHeight: Double,
    val hazard: Hazard,
    val climbable: Boolean,
    val avoidStandingOn: Boolean,
    val destroySpeed: Float,
    val requiresCorrectTool: Boolean,
    val isAir: Boolean,
    val known: Boolean
) {

    val hasCollision: Boolean get() = shapeKind != ShapeKind.EMPTY

    val fullCube: Boolean get() = shapeKind == ShapeKind.FULL_CUBE

    val indestructible: Boolean get() = destroySpeed < 0f

    val hasSupport: Boolean get() = supportTop > NO_SUPPORT

    val isWater: Boolean get() = fluid == FluidKind.WATER

    val isLava: Boolean get() = fluid == FluidKind.LAVA

    val blocksFootprint: Boolean get() = footprintSpans.isNotEmpty()

    fun footprintBlocks(lowAbs: Double, highAbs: Double, cellY: Int): Boolean {
        var i = 0
        while (i < footprintSpans.size) {
            val lo = cellY + footprintSpans[i]
            val hi = cellY + footprintSpans[i + 1]
            if (hi > lowAbs + EPS && lo < highAbs - EPS) return true
            i += 2
        }
        return false
    }

    fun highestSupportBelow(limitAbs: Double, cellY: Int): Double {
        var best = NO_SUPPORT
        var i = 0
        while (i < footprintSpans.size) {
            val hi = cellY + footprintSpans[i + 1]
            if (hi <= limitAbs + EPS && hi > best) best = hi
            i += 2
        }
        return best
    }

    companion object {
        const val NO_SUPPORT = Double.NEGATIVE_INFINITY
        const val EPS = 1.0E-7
    }
}
