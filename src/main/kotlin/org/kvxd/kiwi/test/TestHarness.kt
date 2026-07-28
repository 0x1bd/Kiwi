package org.kvxd.kiwi.test

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.task.Task
import org.kvxd.kiwi.task.TaskStatus
import org.kvxd.kiwi.world.LevelWorldView

class TestFailure(message: String) : AssertionError(message)

fun ClientGameTestContext.onClient(action: (Minecraft) -> Unit) {
    runOnClient<RuntimeException> { action(it) }
}

fun <T> ClientGameTestContext.fromClient(function: (Minecraft) -> T): T =
    computeOnClient<T, RuntimeException> { function(it) }

fun check(condition: Boolean, message: () -> String) {
    if (!condition) throw TestFailure(message())
}

fun <T : Any> checkNotNull(value: T?, message: () -> String): T = value ?: throw TestFailure(message())

fun checkEquals(expected: Any?, actual: Any?, message: () -> String) {
    if (expected != actual) throw TestFailure("${message()} (expected=$expected actual=$actual)")
}

fun checkClose(expected: Double, actual: Double, tolerance: Double, message: () -> String) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw TestFailure("${message()} (expected=$expected actual=$actual tolerance=$tolerance)")
    }
}

class BotTestWorld(
    val context: ClientGameTestContext,
    val singleplayer: TestSingleplayerContext
) {

    private var nextArenaX = 0

    fun command(command: String) {
        singleplayer.server.runCommand(command)
    }

    fun setBlock(pos: BlockPos, block: String) {
        command("setblock ${pos.x} ${pos.y} ${pos.z} $block replace")
    }

    fun fill(from: BlockPos, to: BlockPos, block: String) {
        command("fill ${from.x} ${from.y} ${from.z} ${to.x} ${to.y} ${to.z} $block replace")
    }

    fun clear(from: BlockPos, to: BlockPos) {
        fill(from, to, "minecraft:air")
    }

    fun give(item: String, count: Int = 1) {
        command("give @p $item $count")
    }

    fun teleport(pos: BlockPos) {
        command("tp @p ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5}")
        settle()
    }

    fun gamemode(mode: String) {
        command("gamemode $mode @p")
    }

    fun clearInventory() {
        command("clear @p")
    }

    fun arena(sizeX: Int = 48, sizeZ: Int = 48, floorY: Int = GROUND_Y): BlockPos {
        val originX = ARENA_ORIGIN_X + nextArenaX
        nextArenaX += sizeX + 16
        val origin = BlockPos(originX, floorY, ARENA_ORIGIN_Z)

        val from = BlockPos(origin.x - 2, floorY, origin.z - 2)
        val to = BlockPos(origin.x + sizeX, floorY, origin.z + sizeZ)

        teleport(BlockPos(origin.x + sizeX / 2, floorY + 1, origin.z + sizeZ / 2))
        awaitChunks(from, to)

        fill(from, to, "minecraft:stone")
        clear(
            BlockPos(from.x, floorY + 1, from.z),
            BlockPos(to.x, floorY + 12, to.z)
        )
        settle(6)
        awaitChunks(from, to)
        return origin
    }

    fun awaitChunks(from: BlockPos, to: BlockPos, timeoutTicks: Int = 20 * 30) {
        val minChunkX = from.x shr 4
        val maxChunkX = to.x shr 4
        val minChunkZ = from.z shr 4
        val maxChunkZ = to.z shr 4

        context.waitFor({ client ->
            val level = client.level
            if (level == null) {
                false
            } else {
                var loaded = true
                outer@ for (cx in minChunkX..maxChunkX) {
                    for (cz in minChunkZ..maxChunkZ) {
                        if (level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                            loaded = false
                            break@outer
                        }
                    }
                }
                loaded
            }
        }, timeoutTicks)
    }

    fun settle(ticks: Int = 3) {
        context.waitTicks(ticks)
    }

    fun playerPos(): BlockPos = context.fromClient { it.player!!.blockPosition() }

    fun playerY(): Double = context.fromClient { it.player!!.y }

    fun countItem(itemId: Int): Int = context.fromClient { client ->
        var total = 0
        val inventory = client.player!!.inventory
        for (slot in 0..35) {
            val stack = inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (org.kvxd.kiwi.knowledge.Ids.itemOf(stack) == itemId) total += stack.count
        }
        total
    }

    fun blockAt(pos: BlockPos): String = context.fromClient { client ->
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(client.level!!.getBlockState(pos).block)
            .toString()
    }

    fun isAir(pos: BlockPos): Boolean = context.fromClient { it.level!!.getBlockState(pos).isAir }

    fun <T> withWorldView(action: (LevelWorldView, Level) -> T): T = context.fromClient { client ->
        val level = client.level!!
        action(LevelWorldView(level), level)
    }

    fun runBot(task: Task, timeoutTicks: Int): TaskStatus {
        context.onClient { Bot.start(task) }

        var elapsed = 0
        while (elapsed < timeoutTicks) {
            context.waitTick()
            elapsed++
            if (!context.fromClient { Bot.isBusy }) {
                val result = context.fromClient { Bot.lastResult }
                Kiwi.logger.info("Kiwi test: task finished after $elapsed ticks with $result")
                if (result is TaskStatus.Failure) {
                    val log = context.fromClient { org.kvxd.kiwi.bot.BotLog.history().takeLast(60).joinToString("\n") }
                    Kiwi.logger.error("Kiwi test: trailing bot log\n{}", log)
                }
                return result
            }
        }

        val trail = context.fromClient { Bot.taskTrail().joinToString(" > ") { task -> task.describe() } }
        val log = context.fromClient { org.kvxd.kiwi.bot.BotLog.history().takeLast(40).joinToString("\n") }
        Kiwi.logger.error("Kiwi test: task stalled at [{}]\n{}", trail, log)
        context.onClient { Bot.stop() }
        return TaskStatus.Failure("task did not finish within $timeoutTicks ticks (stalled at $trail)")
    }

    class MotionTrace(
        val ticks: Int,
        val reversals: Int,
        val distanceTravelled: Double,
        val straightLineDistance: Double,
        val result: TaskStatus
    ) {
        val wander: Double get() = if (straightLineDistance < 1.0E-6) 0.0 else distanceTravelled / straightLineDistance
    }

    fun traceBot(task: Task, timeoutTicks: Int): MotionTrace {
        val startPos = context.fromClient { it.player!!.position() }
        context.onClient { Bot.start(task) }

        var ticks = 0
        var reversals = 0
        var travelled = 0.0
        var previous = startPos
        var previousHeading: net.minecraft.world.phys.Vec3? = null
        var result: TaskStatus = TaskStatus.Running

        while (ticks < timeoutTicks) {
            context.waitTick()
            ticks++

            val position = context.fromClient { it.player!!.position() }
            val stepX = position.x - previous.x
            val stepZ = position.z - previous.z
            val stepLength = kotlin.math.sqrt(stepX * stepX + stepZ * stepZ)

            if (stepLength > MOTION_EPSILON) {
                travelled += stepLength
                val heading = net.minecraft.world.phys.Vec3(stepX / stepLength, 0.0, stepZ / stepLength)
                previousHeading?.let { last ->
                    if (heading.x * last.x + heading.z * last.z < REVERSAL_DOT) reversals++
                }
                previousHeading = heading
            }
            previous = position

            if (!context.fromClient { Bot.isBusy }) {
                result = context.fromClient { Bot.lastResult }
                break
            }
        }

        if (context.fromClient { Bot.isBusy }) {
            context.onClient { Bot.stop() }
            result = TaskStatus.Failure("did not finish within $timeoutTicks ticks")
        }

        val end = context.fromClient { it.player!!.position() }
        val straight = kotlin.math.sqrt(
            (end.x - startPos.x) * (end.x - startPos.x) + (end.z - startPos.z) * (end.z - startPos.z)
        )
        return MotionTrace(ticks, reversals, travelled, straight, result)
    }

    fun minedPositions(itemId: Int): List<BlockPos> = context.fromClient {
        Bot.memory.minedCells.toLongArray().map { BlockPos.of(it) }
    }

    fun withBreakingDisabled(body: () -> Unit) {
        context.onClient { org.kvxd.kiwi.config.ConfigData.allowBreak = false }
        try {
            body()
        } finally {
            context.onClient { org.kvxd.kiwi.config.ConfigData.allowBreak = true }
        }
    }

    fun runBotAvoiding(task: Task, timeoutTicks: Int, forbidden: BlockPos): Pair<TaskStatus, Boolean> {
        context.onClient { Bot.start(task) }
        var entered = false
        var elapsed = 0

        while (elapsed < timeoutTicks) {
            context.waitTick()
            elapsed++
            if (playerPos() == forbidden) entered = true
            if (!context.fromClient { Bot.isBusy }) {
                return context.fromClient { Bot.lastResult } to entered
            }
        }

        context.onClient { Bot.stop() }
        return TaskStatus.Failure("task did not finish within $timeoutTicks ticks") to entered
    }

    fun expectSuccess(task: Task, timeoutTicks: Int, what: String) {
        when (val result = runBot(task, timeoutTicks)) {
            is TaskStatus.Success -> Unit
            is TaskStatus.Failure -> throw TestFailure("$what failed: ${result.reason}")
            else -> throw TestFailure("$what ended in unexpected state $result")
        }
    }

    companion object {
        const val ARENA_ORIGIN_X = 0
        const val ARENA_ORIGIN_Z = 96
        const val GROUND_Y = -61

        private const val MOTION_EPSILON = 0.01
        private const val REVERSAL_DOT = -0.35
    }
}
