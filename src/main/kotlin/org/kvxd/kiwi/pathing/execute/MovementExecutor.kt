package org.kvxd.kiwi.pathing.execute

import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.calc.NodePath

interface MovementExecutor {

    fun execute(node: Node, path: NodePath)

    fun isFinished(node: Node): Boolean

    val deviationThreshold: Double
        get() = ConfigManager.data.horizontalDeviationThreshold
}