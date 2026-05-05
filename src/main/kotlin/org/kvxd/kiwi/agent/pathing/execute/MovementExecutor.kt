package org.kvxd.kiwi.agent.pathing.execute

import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.calc.NodePath

interface MovementExecutor {

    fun execute(node: Node, path: NodePath)

    fun isFinished(node: Node): Boolean

    val deviationThreshold: Double
        get() = ConfigData.horizontalDeviationThreshold
}