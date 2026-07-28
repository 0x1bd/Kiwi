package org.kvxd.kiwi.nav

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.bot.BotLog
import org.kvxd.kiwi.control.Controller
import org.kvxd.kiwi.level
import org.kvxd.kiwi.path.Path
import org.kvxd.kiwi.path.PathContext
import org.kvxd.kiwi.path.PathFailure
import org.kvxd.kiwi.path.PathGoal
import org.kvxd.kiwi.path.PathResult
import org.kvxd.kiwi.path.PathSearch
import org.kvxd.kiwi.path.PathStatus
import org.kvxd.kiwi.player
import org.kvxd.kiwi.world.LevelWorldView
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

sealed interface NavStatus {
    data object Idle : NavStatus
    data object Calculating : NavStatus
    data object Following : NavStatus
    data object Reached : NavStatus
    data class Failed(val reason: String) : NavStatus
}

class Navigator(private val policy: () -> PathContext) {

    private var goal: PathGoal? = null
    private var pending: Future<PathResult>? = null
    private var executor: PathExecutor? = null
    private var liveView: LevelWorldView? = null

    private var lastPlanAtMs = 0L
    private var replanReason: String? = "initial"
    private var failure: String? = null
    private var consecutiveFailedSearches = 0

    private var lastPosition: Vec3 = Vec3.ZERO
    private var stillTicks = 0

    var lastResult: PathResult? = null
    private set

    val path: Path get() = executor?.let { currentPath } ?: Path.EMPTY

    private var currentPath: Path = Path.EMPTY

    val currentIndex: Int get() = executor?.index ?: 0

    val isCalculating: Boolean get() = pending != null

    fun start(goal: PathGoal) {
        cancel()
        this.goal = goal
        replanReason = "initial"
        failure = null
        consecutiveFailedSearches = 0
        stillTicks = 0
        lastPlanAtMs = 0L
        lastPosition = player.position()
    }

    fun cancel() {
        pending?.cancel(true)
        pending = null
        executor?.stop()
        executor = null
        currentPath = Path.EMPTY
        goal = null
        liveView = null
        Controller.stopMoving()
    }

    fun tick(): NavStatus {
        val goal = this.goal ?: return NavStatus.Idle
        failure?.let { return NavStatus.Failed(it) }

        val view = liveView ?: LevelWorldView(level).also { liveView = it }
        view.invalidateAll()

        if (goal.isReached(player.blockPosition().x, player.blockPosition().y, player.blockPosition().z)) {
            cancel()
            return NavStatus.Reached
        }

        collectSearchResult(goal)

        val follower = executor
        if (follower == null) {
            requestReplan("no path")
            dispatchSearch(goal)
            return if (failure != null) NavStatus.Failed(failure!!) else NavStatus.Calculating
        }

        updateStuckDetection(follower)

        when (val state = follower.tick()) {
            FollowState.Following -> Unit
            FollowState.Finished -> {
                if (currentPath.isPartial) {
                    requestReplan("partial path consumed")
                } else {
                    val at = player.blockPosition()
                    if (goal.isReached(at.x, at.y, at.z)) {
                        cancel()
                        return NavStatus.Reached
                    }
                    requestReplan("path finished short of goal")
                }
            }

            is FollowState.Diverged -> {
                BotLog.debug { "follower diverged: ${state.reason}" }
                requestReplan(state.reason)
            }
        }

        if (currentPath.isPartial && follower.remaining <= PARTIAL_LOOKAHEAD) {
            requestReplan("refreshing partial path")
        }

        dispatchSearch(goal)
        return if (failure != null) NavStatus.Failed(failure!!) else NavStatus.Following
    }

    fun requestReplan(reason: String) {
        if (replanReason == null) replanReason = reason
    }

    private fun dispatchSearch(goal: PathGoal) {
        if (pending != null) return
        val reason = replanReason ?: return

        val now = System.currentTimeMillis()
        if (executor != null && now - lastPlanAtMs < REPLAN_COOLDOWN_MS) return

        replanReason = null
        lastPlanAtMs = now

        val searchContext = policy().copy(view = LevelWorldView(level))
        val start = player.blockPosition()
        val feetY = player.y

        BotLog.debug { "searching ($reason) -> ${goal.describe()} from ${player.blockPosition().toShortString()}" }
        pending = searchPool.submit<PathResult> { PathSearch(searchContext).search(start, feetY, goal) }
    }

    private fun collectSearchResult(goal: PathGoal) {
        val task = pending ?: return
        if (!task.isDone) return
        pending = null

        val result = try {
            task.get()
        } catch (e: Throwable) {
            Kiwi.logger.warn("Kiwi path search failed", e)
            failure = "path search crashed: ${e.message ?: e::class.simpleName}"
            return
        }
        lastResult = result

        if (!result.succeeded) {
            consecutiveFailedSearches++
            BotLog.debug { "search unsuccessful: ${result.failure?.message} (attempt $consecutiveFailedSearches)" }
            if (result.failure == PathFailure.Unloaded && consecutiveFailedSearches < MAX_FAILED_SEARCHES) {
                requestReplan("waiting for chunks")
                return
            }
            if (executor != null && consecutiveFailedSearches < MAX_FAILED_SEARCHES) {
                requestReplan("retrying after ${result.failure?.message}")
                return
            }
            failure = result.failure?.message ?: "no path"
            BotLog.debug { "search failed permanently: $failure" }
            return
        }

        consecutiveFailedSearches = 0
        currentPath = result.path
        val view = liveView ?: LevelWorldView(level).also { liveView = it }
        executor?.stop()
        executor = PathExecutor(result.path, policy().copy(view = view), view)
        stillTicks = 0
        Controller.engage()

        BotLog.debug {
            "accepted ${result.path.status} path of ${result.path.size} nodes in ${"%.1f".format(result.durationMs)}ms"
        }
    }

    private fun updateStuckDetection(follower: PathExecutor) {
        val position = player.position()
        if (position.distanceToSqr(lastPosition) < STILL_EPSILON_SQ) {
            stillTicks++
        } else {
            stillTicks = 0
            lastPosition = position
        }

        val node = follower.currentNode
        val breaking = node != null && node.breaks.isNotEmpty()
        val vertical = node != null && !node.kind.smoothable

        if (!breaking && !vertical && stillTicks > STUCK_TICKS) {
            stillTicks = 0
            requestReplan("stuck for $STUCK_TICKS ticks")
        }
    }

    fun destination(): BlockPos? = currentPath.destination()?.pos()

    companion object {
        private const val REPLAN_COOLDOWN_MS = 350L
        private const val PARTIAL_LOOKAHEAD = 3
        private const val STUCK_TICKS = 45
        private const val STILL_EPSILON_SQ = 0.0009
        private const val MAX_FAILED_SEARCHES = 6

        private val searchPool = Executors.newFixedThreadPool(
            2,
            object : ThreadFactory {
                private val counter = AtomicInteger()
                override fun newThread(runnable: Runnable) =
                    Thread(runnable, "kiwi-path-${counter.incrementAndGet()}").apply { isDaemon = true }
            }
        )
    }
}
