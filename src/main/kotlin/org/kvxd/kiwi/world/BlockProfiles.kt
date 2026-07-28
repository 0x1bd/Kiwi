package org.kvxd.kiwi.world

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.WitherRoseBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.concurrent.ConcurrentHashMap

object BlockProfiles {

    const val FOOTPRINT_MIN = 0.5 - PlayerBox.HALF_WIDTH
    const val FOOTPRINT_MAX = 0.5 + PlayerBox.HALF_WIDTH

    private val cache = ConcurrentHashMap<BlockState, BlockProfile>()

    val unknown: BlockProfile = BlockProfile(
        state = Blocks.BEDROCK.defaultBlockState(),
        shapeKind = ShapeKind.FULL_CUBE,
        shape = Shapes.block(),
        footprintSpans = doubleArrayOf(0.0, 1.0),
        supportTop = BlockProfile.NO_SUPPORT,
        fluid = FluidKind.NONE,
        fluidHeight = 0.0,
        hazard = Hazard.NONE,
        climbable = false,
        avoidStandingOn = true,
        destroySpeed = -1f,
        requiresCorrectTool = true,
        isAir = false,
        known = false
    )

    val outsideWorld: BlockProfile = BlockProfile(
        state = Blocks.AIR.defaultBlockState(),
        shapeKind = ShapeKind.EMPTY,
        shape = Shapes.empty(),
        footprintSpans = DoubleArray(0),
        supportTop = BlockProfile.NO_SUPPORT,
        fluid = FluidKind.NONE,
        fluidHeight = 0.0,
        hazard = Hazard.NONE,
        climbable = false,
        avoidStandingOn = false,
        destroySpeed = -1f,
        requiresCorrectTool = false,
        isAir = true,
        known = true
    )

    fun of(state: BlockState, level: BlockGetter, pos: BlockPos): BlockProfile {
        if (state.block.hasDynamicShape()) return compute(state, level, pos)
        return cache.computeIfAbsent(state) { compute(it, EmptyBlockGetter.INSTANCE, BlockPos.ZERO) }
    }

    fun clear() = cache.clear()

    private fun compute(state: BlockState, level: BlockGetter, pos: BlockPos): BlockProfile {
        val shape = runCatching { state.getCollisionShape(level, pos) }.getOrElse { Shapes.empty() }
        val kind = when {
            shape.isEmpty -> ShapeKind.EMPTY
            Block.isShapeFullBlock(shape) -> ShapeKind.FULL_CUBE
            else -> ShapeKind.PARTIAL
        }

        val spans = when (kind) {
            ShapeKind.EMPTY -> DoubleArray(0)
            ShapeKind.FULL_CUBE -> doubleArrayOf(0.0, 1.0)
            ShapeKind.PARTIAL -> footprintSpansOf(shape)
        }

        var supportTop = BlockProfile.NO_SUPPORT
        var i = 1
        while (i < spans.size) {
            if (spans[i] > supportTop) supportTop = spans[i]
            i += 2
        }

        val fluidState = state.fluidState
        val fluid = when {
            fluidState.isEmpty -> FluidKind.NONE
            fluidState.`is`(FluidTags.LAVA) -> FluidKind.LAVA
            fluidState.`is`(FluidTags.WATER) -> FluidKind.WATER
            else -> FluidKind.NONE
        }

        val block = state.block
        val hazard = when {
            fluid == FluidKind.LAVA -> Hazard.LAVA
            block is FireBlock || block is CactusBlock || block is SweetBerryBushBlock ||
                block is WitherRoseBlock || block is CampfireBlock -> Hazard.CONTACT_DAMAGE
            block is PowderSnowBlock -> Hazard.SINKING
            block is MagmaBlock -> Hazard.CONTACT_DAMAGE
            else -> Hazard.NONE
        }

        return BlockProfile(
            state = state,
            shapeKind = kind,
            shape = shape,
            footprintSpans = spans,
            supportTop = supportTop,
            fluid = fluid,
            fluidHeight = if (fluid == FluidKind.NONE) 0.0 else fluidState.ownHeight.toDouble(),
            hazard = hazard,
            climbable = state.`is`(BlockTags.CLIMBABLE),
            avoidStandingOn = hazard != Hazard.NONE || block is MagmaBlock,
            destroySpeed = runCatching { state.getDestroySpeed(level, pos) }.getOrDefault(-1f),
            requiresCorrectTool = state.requiresCorrectToolForDrops(),
            isAir = state.isAir,
            known = true
        )
    }

    private fun footprintSpansOf(shape: VoxelShape): DoubleArray {
        val boxes = shape.toAabbs()
        if (boxes.isEmpty()) return DoubleArray(0)

        val lows = ArrayList<Double>(boxes.size)
        val highs = ArrayList<Double>(boxes.size)
        for (box in boxes) {
            if (!overlapsFootprint(box)) continue
            lows.add(box.minY)
            highs.add(box.maxY)
        }
        if (lows.isEmpty()) return DoubleArray(0)

        val order = lows.indices.sortedBy { lows[it] }
        val merged = ArrayList<Double>(order.size * 2)
        var currentLo = lows[order[0]]
        var currentHi = highs[order[0]]
        for (idx in 1 until order.size) {
            val lo = lows[order[idx]]
            val hi = highs[order[idx]]
            if (lo <= currentHi + BlockProfile.EPS) {
                if (hi > currentHi) currentHi = hi
            } else {
                merged.add(currentLo)
                merged.add(currentHi)
                currentLo = lo
                currentHi = hi
            }
        }
        merged.add(currentLo)
        merged.add(currentHi)

        return DoubleArray(merged.size) { merged[it] }
    }

    private fun overlapsFootprint(box: AABB): Boolean =
        box.maxX > FOOTPRINT_MIN + BlockProfile.EPS &&
            box.minX < FOOTPRINT_MAX - BlockProfile.EPS &&
            box.maxZ > FOOTPRINT_MIN + BlockProfile.EPS &&
            box.minZ < FOOTPRINT_MAX - BlockProfile.EPS
}
