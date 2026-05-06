package org.kvxd.kiwi.agent.runtime.actions

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.RotationManager
import org.kvxd.kiwi.agent.control.NavigationResult
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.pathing.goal.goals.GoalNear
import org.kvxd.kiwi.agent.runtime.AgentPhase
import org.kvxd.kiwi.agent.runtime.AgentRuntime
import org.kvxd.kiwi.agent.runtime.AgentFailure
import org.kvxd.kiwi.util.math.RotationUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

suspend fun AgentRuntime.walkTo(pos: BlockPos, reach: Double = 1.5) {
    phase = AgentPhase.MOVING
    val goal = GoalNear(pos, reach)
    when (val result = PathNavigator.navigateToGoal(goal)) {
        NavigationResult.Reached -> return
        is NavigationResult.Failed -> {
            throw AgentFailure("Could not reach $pos: ${result.reason.describe()}")
        }
    }
}

suspend fun AgentRuntime.lookAtBlock(pos: BlockPos) {
    val rots = RotationUtils.getLookRotations(Vec3.atCenterOf(pos))
    RotationManager.setTarget(rots.x, rots.y)
    delay(50.milliseconds)
}
