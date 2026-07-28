package org.kvxd.kiwi.world

import kotlin.math.floor

object Stances {

    const val NONE = Double.NaN

    fun isValid(feetY: Double): Boolean = !feetY.isNaN()

    fun feetHeight(view: WorldView, x: Int, y: Int, z: Int): Double {
        var best = Double.NEGATIVE_INFINITY

        val below = view.profile(x, y - 1, z)
        if (below.known && below.hasSupport) {
            val candidate = (y - 1) + below.supportTop
            if (candidate >= y - BlockProfile.EPS && candidate < y + 1.0 - BlockProfile.EPS) best = candidate
        }

        val self = view.profile(x, y, z)
        if (self.known && self.hasSupport) {
            val candidate = y + self.supportTop
            if (candidate >= y - BlockProfile.EPS && candidate < y + 1.0 - BlockProfile.EPS && candidate > best) {
                best = candidate
            }
        }

        return if (best == Double.NEGATIVE_INFINITY) NONE else best
    }

    fun supportCellY(view: WorldView, x: Int, y: Int, z: Int, feetY: Double): Int {
        val self = view.profile(x, y, z)
        if (self.known && self.hasSupport && Math.abs(y + self.supportTop - feetY) < 1.0E-6) return y
        return y - 1
    }

    fun hasClearance(
        view: WorldView,
        x: Int,
        z: Int,
        feetY: Double,
        height: Double = PlayerBox.HEIGHT
    ): Boolean {
        val top = feetY + height
        var cellY = floor(feetY).toInt() - 1
        val lastCell = floor(top - BlockProfile.EPS).toInt()

        while (cellY <= lastCell) {
            val profile = view.profile(x, cellY, z)
            if (!profile.known) return false
            if (profile.blocksFootprint && profile.footprintBlocks(feetY, top, cellY)) return false
            cellY++
        }
        return true
    }

    fun standingFeetHeight(
        view: WorldView,
        x: Int,
        y: Int,
        z: Int,
        height: Double = PlayerBox.HEIGHT
    ): Double {
        val feetY = feetHeight(view, x, y, z)
        if (!isValid(feetY)) return NONE
        if (!hasClearance(view, x, z, feetY, height)) return NONE
        return feetY
    }

    fun landingBelow(view: WorldView, x: Int, z: Int, fromY: Double, maxDrop: Int): Double {
        var cellY = floor(fromY).toInt()
        val limit = cellY - maxDrop - 1
        while (cellY >= limit && cellY >= view.minY) {
            val profile = view.profile(x, cellY, z)
            if (!profile.known) return NONE
            val support = profile.highestSupportBelow(fromY, cellY)
            if (support > Double.NEGATIVE_INFINITY && support < fromY - BlockProfile.EPS) return support
            if (profile.fluid != FluidKind.NONE) return cellY.toDouble()
            cellY--
        }
        return NONE
    }

    fun isSwimmable(view: WorldView, x: Int, y: Int, z: Int): Boolean {
        val profile = view.profile(x, y, z)
        if (!profile.known || profile.fluid != FluidKind.WATER) return false
        return !profile.blocksFootprint
    }
}
