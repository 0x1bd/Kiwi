package org.kvxd.kiwi.control

import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.bot.BotLog
import org.kvxd.kiwi.player
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Steering {

    private const val HORIZON = 8
    private const val LATERAL_WEIGHT = 2.5
    private const val ARRIVAL_SPEED_PENALTY = 2.0

    private class Direction(
        val forward: Boolean,
        val back: Boolean,
        val left: Boolean,
        val right: Boolean,
        val localX: Double,
        val localZ: Double
    )

    private val directions: List<Direction> = buildList {
        val axes = listOf(
            Triple(true, false, 0.0 to 1.0),
            Triple(false, true, 0.0 to -1.0)
        )
        for (forward in booleanArrayOf(true, false)) {
            for (back in booleanArrayOf(true, false)) {
                if (forward && back) continue
                for (left in booleanArrayOf(true, false)) {
                    for (right in booleanArrayOf(true, false)) {
                        if (left && right) continue
                        val z = axis(forward, back)
                        val x = axis(left, right)
                        if (x == 0.0 && z == 0.0) continue
                        val length = sqrt(x * x + z * z)
                        add(Direction(forward, back, left, right, x / length, z / length))
                    }
                }
            }
        }
        axes.size
    }

    private fun axis(positive: Boolean, negative: Boolean): Double = when {
        positive == negative -> 0.0
        positive -> 1.0
        else -> -1.0
    }

    fun stop() {
        Controller.stopMoving()
    }

    fun driveTo(target: Vec3, sprint: Boolean = false, brake: Boolean = true) {
        LookController.faceHorizontally(player.position(), target)
        choose(sprint) { end ->
            val dx = end.x - target.x
            val dz = end.z - target.z
            sqrt(dx * dx + dz * dz) + if (brake) end.speed * ARRIVAL_SPEED_PENALTY else 0.0
        }
    }

    fun driveSegment(from: Vec3, to: Vec3, sprint: Boolean = false) {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0E-6) {
            driveTo(to, sprint, brake = false)
            return
        }

        val ux = dx / length
        val uz = dz / length
        LookController.faceHorizontally(player.position(), to)

        choose(sprint) { end ->
            val relX = end.x - from.x
            val relZ = end.z - from.z
            val along = relX * ux + relZ * uz
            val lateral = abs(relX * -uz + relZ * ux)
            val remaining = (length - along).coerceAtLeast(0.0)
            remaining + lateral * LATERAL_WEIGHT
        }
    }

    fun progressAlong(from: Vec3, to: Vec3): Double {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val lengthSq = dx * dx + dz * dz
        if (lengthSq < 1.0E-9) return 1.0
        return ((player.x - from.x) * dx + (player.z - from.z) * dz) / lengthSq
    }

    private inline fun choose(sprint: Boolean, score: (MotionState) -> Double) {
        val parameters = MotionModel.sample(sprint)
        val start = MotionModel.current()
        val yaw = LookController.effectiveYaw() * PI / 180.0
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)

        var best: Direction? = null
        var bestHold = 0
        var bestScore = score(MotionModel.rollout(start, 0.0, 0.0, parameters, HORIZON))

        for (direction in directions) {
            val worldX = direction.localX * cosYaw - direction.localZ * sinYaw
            val worldZ = direction.localZ * cosYaw + direction.localX * sinYaw

            var state = start
            for (hold in 1..HORIZON) {
                state = MotionModel.step(state, worldX, worldZ, parameters)
                val coasted = MotionModel.rollout(state, 0.0, 0.0, parameters, HORIZON - hold)
                val candidate = score(coasted)
                if (candidate < bestScore) {
                    bestScore = candidate
                    best = direction
                    bestHold = hold
                }
            }
        }

        val chosen = best
        if (chosen == null) {
            Controller.stopMoving()
            trace(null, bestScore, start, parameters, 0)
            return
        }

        val input = Controller.input()
        input.forward = chosen.forward
        input.back = chosen.back
        input.left = chosen.left
        input.right = chosen.right
        input.sprint = sprint && chosen.forward && !chosen.back
        trace(chosen, bestScore, start, parameters, bestHold)
    }

    private var traceTicks = 0

    private fun trace(
        chosen: Direction?,
        score: Double,
        start: MotionState,
        parameters: MotionParameters,
        hold: Int
    ) {
        if (traceTicks++ % 40 != 0) return
        BotLog.debug {
            val keys = if (chosen == null) "idle" else
                "f=${chosen.forward} b=${chosen.back} l=${chosen.left} r=${chosen.right}"
            "steer $keys hold=$hold score=${"%.2f".format(score)} " +
                "pos=(${"%.2f".format(start.x)},${"%.2f".format(start.z)}) " +
                "v=${"%.3f".format(start.speed)} terminal=${"%.3f".format(parameters.terminalSpeed)}"
        }
    }
}
