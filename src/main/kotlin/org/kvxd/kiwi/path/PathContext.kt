package org.kvxd.kiwi.path

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import org.kvxd.kiwi.world.BlockProfile
import org.kvxd.kiwi.world.Hazard
import org.kvxd.kiwi.world.WorldView

enum class BreakPolicy {
    NEVER,
    SAFE_ONLY,
    ANY
}

data class PathContext(
    val view: WorldView,
    val breakPolicy: BreakPolicy = BreakPolicy.ANY,
    val allowPlace: Boolean = true,
    val allowWater: Boolean = true,
    val allowDiagonals: Boolean = true,
    val maxFallBlocks: Int = 3,
    val placementBudget: Int = 0,
    val breakTicks: (BlockProfile) -> Double = { defaultBreakTicks(it) },
    val safeToBreak: (BlockProfile) -> Boolean = { true },
    val protectedCells: LongSet = LongOpenHashSet(),
    val maxSearchIterations: Int = 24_000,
    val placeable: (net.minecraft.world.item.ItemStack) -> Boolean = { it.item is net.minecraft.world.item.BlockItem }
) {

    fun canBreak(profile: BlockProfile, cell: Long): Boolean {
        if (breakPolicy == BreakPolicy.NEVER) return false
        if (!profile.known) return false
        if (profile.indestructible) return false
        if (profile.fluid != org.kvxd.kiwi.world.FluidKind.NONE) return false
        if (cell in protectedCells) return false
        if (breakPolicy == BreakPolicy.SAFE_ONLY && !safeToBreak(profile)) return false
        return true
    }

    fun breakCost(profile: BlockProfile): Double =
        MoveCosts.BREAK_OVERHEAD + breakTicks(profile) * MoveCosts.BREAK_TICK_SCALE

    fun isHazard(profile: BlockProfile): Boolean =
        profile.hazard != Hazard.NONE || (!allowWater && profile.isWater)

    companion object {
        fun defaultBreakTicks(profile: BlockProfile): Double {
            val hardness = profile.destroySpeed
            if (hardness < 0f) return Double.POSITIVE_INFINITY
            if (hardness == 0f) return 0.0
            return (hardness * 20.0).coerceAtMost(200.0)
        }
    }
}
