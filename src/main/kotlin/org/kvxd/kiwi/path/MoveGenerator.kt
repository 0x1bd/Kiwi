package org.kvxd.kiwi.path

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.world.BlockProfile
import org.kvxd.kiwi.world.FluidKind
import org.kvxd.kiwi.world.PlayerBox
import org.kvxd.kiwi.world.Stances
import org.kvxd.kiwi.world.WorldView
import org.kvxd.kiwi.world.collides
import kotlin.math.abs
import kotlin.math.floor

class MoveGenerator(private val ctx: PathContext) {

    private val view: WorldView get() = ctx.view

    private val cardinals = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
    private val diagonals = intArrayOf(1, 1, 1, -1, -1, 1, -1, -1)

    fun generate(node: PathNode, out: MoveBuffer) {
        out.clear()

        val inWater = view.profile(node.x, node.y, node.z).isWater
        if (inWater && ctx.allowWater) {
            generateSwim(node, out)
        }

        generateHorizontal(node, out, inWater)
        generateVertical(node, out)
    }

    private fun generateHorizontal(node: PathNode, out: MoveBuffer, inWater: Boolean) {
        var i = 0
        while (i < cardinals.size) {
            step(node, cardinals[i], cardinals[i + 1], false, out, inWater)
            i += 2
        }
        if (!ctx.allowDiagonals) return
        i = 0
        while (i < diagonals.size) {
            step(node, diagonals[i], diagonals[i + 1], true, out, inWater)
            i += 2
        }
    }

    private fun step(
        node: PathNode,
        dx: Int,
        dz: Int,
        diagonal: Boolean,
        out: MoveBuffer,
        inWater: Boolean
    ) {
        val tx = node.x + dx
        val tz = node.z + dz
        val baseCost = if (diagonal) MoveCosts.DIAGONAL else MoveCosts.WALK

        var landed = false
        for (ty in node.y + 1 downTo node.y - 1) {
            val feet = Stances.standingFeetHeight(view, tx, ty, tz)
            if (!Stances.isValid(feet)) continue
            if (!isSafeStance(tx, ty, tz, feet)) continue

            val rise = feet - node.feetY

            if (rise > PlayerBox.JUMP_HEIGHT + BlockProfile.EPS) continue
            if (rise < -1.05) continue

            if (diagonal) {
                if (abs(rise) > BlockProfile.EPS) continue
                if (!diagonalCorridorClear(node, tx, tz, feet)) continue
            }

            if (rise <= PlayerBox.STEP_HEIGHT + BlockProfile.EPS) {
                if (!transitionClear(node, tx, tz, rise)) continue
                if (rise < -BlockProfile.EPS) {
                    out.add(tx, ty, tz, feet, baseCost + MoveCosts.STEP_DOWN, MoveKind.WALK)
                } else {
                    val extra = if (rise > BlockProfile.EPS) MoveCosts.STEP_UP else 0.0
                    out.add(tx, ty, tz, feet, baseCost + extra, MoveKind.WALK)
                }
                landed = true
                break
            }

            if (diagonal) continue
            if (!Stances.hasClearance(view, node.x, node.z, node.feetY, PlayerBox.HEIGHT + rise)) continue
            out.add(tx, ty, tz, feet, baseCost + MoveCosts.JUMP_BASE + rise, MoveKind.JUMP)
            landed = true
            break
        }

        if (inWater) return
        if (diagonal) return

        if (!landed) tryFall(node, tx, tz, out)
        tryBreakThrough(node, tx, tz, out)
    }

    private fun tryFall(node: PathNode, tx: Int, tz: Int, out: MoveBuffer) {
        if (!Stances.hasClearance(view, tx, tz, node.feetY, PlayerBox.HEIGHT)) return

        val landing = Stances.landingBelow(view, tx, tz, node.feetY, ctx.maxFallBlocks)
        if (!Stances.isValid(landing)) return

        val drop = node.feetY - landing
        if (drop <= 0.0) return

        val cellY = floor(landing + BlockProfile.EPS).toInt()
        val water = view.profile(tx, cellY, tz).isWater
        if (!water && drop > ctx.maxFallBlocks + BlockProfile.EPS) return
        if (water && !ctx.allowWater) return

        if (!Stances.hasClearance(view, tx, tz, landing, PlayerBox.HEIGHT)) return
        if (!isSafeStance(tx, cellY, tz, landing)) return

        out.add(tx, cellY, tz, landing, MoveCosts.WALK + drop * MoveCosts.FALL_PER_BLOCK, MoveKind.FALL)
    }

    private fun tryBreakThrough(node: PathNode, tx: Int, tz: Int, out: MoveBuffer) {
        if (ctx.breakPolicy == BreakPolicy.NEVER) return

        for (ty in node.y downTo node.y - 1) {
            val supportProfile = view.profile(tx, ty - 1, tz)
            val selfProfile = view.profile(tx, ty, tz)
            if (!supportProfile.known || !selfProfile.known) continue

            val feet = ty.toDouble()
            val standsOnSupport = supportProfile.hasSupport &&
                abs((ty - 1) + supportProfile.supportTop - feet) < 1.0E-6
            if (!standsOnSupport) continue
            val rise = feet - node.feetY
            if (rise > PlayerBox.STEP_HEIGHT + BlockProfile.EPS || rise < -1.05) continue
            if (ctx.isHazard(supportProfile)) continue

            var cost = MoveCosts.WALK
            var count = 0
            val pending = LongArray(2)
            var blocked = false

            for (offset in 0..1) {
                val cellY = ty + offset
                val profile = view.profile(tx, cellY, tz)
                if (!profile.known) {
                    blocked = true
                    break
                }
                if (!profile.footprintBlocks(feet, feet + PlayerBox.HEIGHT, cellY)) continue
                val cell = BlockPos.asLong(tx, cellY, tz)
                if (!ctx.canBreak(profile, cell)) {
                    blocked = true
                    break
                }
                val breakCost = ctx.breakCost(profile)
                if (breakCost.isInfinite()) {
                    blocked = true
                    break
                }
                pending[count++] = cell
                cost += breakCost
            }

            if (blocked || count == 0) continue
            out.add(tx, ty, tz, feet, cost, MoveKind.WALK, pending.copyOf(count))
            return
        }
    }

    private fun generateVertical(node: PathNode, out: MoveBuffer) {
        generateClimb(node, out)
        generatePillar(node, out)
        generateDescend(node, out)
    }

    private fun generateClimb(node: PathNode, out: MoveBuffer) {
        val here = view.profile(node.x, node.y, node.z)
        if (!here.climbable) return

        val above = view.profile(node.x, node.y + 1, node.z)
        if (above.known && above.climbable && Stances.hasClearance(view, node.x, node.z, node.y + 1.0)) {
            out.add(node.x, node.y + 1, node.z, node.y + 1.0, MoveCosts.CLIMB, MoveKind.CLIMB_UP)
        }

        val below = view.profile(node.x, node.y - 1, node.z)
        if (below.known && (below.climbable || below.hasSupport)) {
            val feet = if (below.climbable) node.y - 1.0 else Stances.feetHeight(view, node.x, node.y - 1, node.z)
            if (Stances.isValid(feet)) {
                out.add(node.x, node.y - 1, node.z, feet, MoveCosts.CLIMB, MoveKind.CLIMB_DOWN)
            }
        }
    }

    private fun generatePillar(node: PathNode, out: MoveBuffer) {
        if (!ctx.allowPlace) return
        if (node.placements >= ctx.placementBudget) return

        val feetCell = floor(node.feetY + BlockProfile.EPS).toInt()
        val placeCell = BlockPos.asLong(node.x, feetCell, node.z)
        if (placeCell in ctx.protectedCells) return

        val supportTarget = view.profile(node.x, feetCell, node.z)
        if (!supportTarget.known) return
        if (supportTarget.hasCollision) return
        if (supportTarget.fluid != FluidKind.NONE) return

        val destinationFeet = feetCell + 1.0
        if (!Stances.hasClearance(view, node.x, node.z, destinationFeet)) return

        out.add(
            node.x,
            feetCell + 1,
            node.z,
            destinationFeet,
            MoveCosts.PILLAR,
            MoveKind.PILLAR,
            NO_BREAKS,
            placeCell
        )
    }

    private fun generateDescend(node: PathNode, out: MoveBuffer) {
        if (ctx.breakPolicy == BreakPolicy.NEVER) return

        val supportCellY = floor(node.feetY - BlockProfile.EPS).toInt()
        val support = view.profile(node.x, supportCellY, node.z)
        val cell = BlockPos.asLong(node.x, supportCellY, node.z)
        if (!ctx.canBreak(support, cell)) return
        if (!support.fullCube) return

        val below = view.profile(node.x, supportCellY - 1, node.z)
        if (!below.known) return
        if (below.fluid != FluidKind.NONE || below.hazard != org.kvxd.kiwi.world.Hazard.NONE) return
        if (!below.hasSupport) return

        val feet = (supportCellY - 1) + below.supportTop
        if (feet <= supportCellY - 1.0 || feet > supportCellY.toDouble()) return
        val destCellY = floor(feet + BlockProfile.EPS).toInt()
        if (!Stances.hasClearance(view, node.x, node.z, feet)) return

        val breakCost = ctx.breakCost(support)
        if (breakCost.isInfinite()) return

        out.add(
            node.x,
            destCellY,
            node.z,
            feet,
            MoveCosts.DESCEND + breakCost,
            MoveKind.DESCEND,
            longArrayOf(cell)
        )
    }

    private fun generateSwim(node: PathNode, out: MoveBuffer) {
        val offsets = intArrayOf(1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1, 0, 0, -1, 0)
        var i = 0
        while (i < offsets.size) {
            val tx = node.x + offsets[i]
            val ty = node.y + offsets[i + 1]
            val tz = node.z + offsets[i + 2]
            i += 3

            if (!Stances.isSwimmable(view, tx, ty, tz)) continue
            if (!Stances.hasClearance(view, tx, tz, ty.toDouble(), PlayerBox.HEIGHT)) continue
            out.add(tx, ty, tz, ty.toDouble(), MoveCosts.SWIM, MoveKind.SWIM)
        }
    }

    private fun transitionClear(node: PathNode, tx: Int, tz: Int, rise: Double): Boolean {
        if (rise < -BlockProfile.EPS) {
            return Stances.hasClearance(view, tx, tz, node.feetY, PlayerBox.HEIGHT)
        }
        if (rise > BlockProfile.EPS) {
            return Stances.hasClearance(view, node.x, node.z, node.feetY, PlayerBox.HEIGHT + rise)
        }
        return true
    }

    private fun isSafeStance(x: Int, y: Int, z: Int, feetY: Double): Boolean {
        val here = view.profile(x, y, z)
        if (ctx.isHazard(here)) return false
        if (here.isWater && !ctx.allowWater) return false

        val supportY = Stances.supportCellY(view, x, y, z, feetY)
        val support = view.profile(x, supportY, z)
        if (support.avoidStandingOn) return false
        if (support.isLava) return false

        val head = view.profile(x, y + 1, z)
        if (head.hazard == org.kvxd.kiwi.world.Hazard.LAVA) return false
        return true
    }

    private fun diagonalCorridorClear(node: PathNode, tx: Int, tz: Int, targetFeet: Double): Boolean {
        val low = minOf(node.feetY, targetFeet)
        val high = maxOf(node.feetY, targetFeet)

        if (!Stances.hasClearance(view, tx, node.z, low, PlayerBox.HEIGHT + (high - low))) return false
        if (!Stances.hasClearance(view, node.x, tz, low, PlayerBox.HEIGHT + (high - low))) return false

        val box = net.minecraft.world.phys.AABB(
            minOf(node.x, tx) + 0.5 - PlayerBox.HALF_WIDTH,
            low,
            minOf(node.z, tz) + 0.5 - PlayerBox.HALF_WIDTH,
            maxOf(node.x, tx) + 0.5 + PlayerBox.HALF_WIDTH,
            high + PlayerBox.HEIGHT,
            maxOf(node.z, tz) + 0.5 + PlayerBox.HALF_WIDTH
        ).deflate(BlockProfile.EPS)

        return !view.collides(box)
    }
}
