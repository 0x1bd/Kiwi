package org.kvxd.kiwi.pathing.calc

data class PathResult(
    val path: NodePath?,
    val nodesVisited: Int,
    val timeComputedMs: Double,
    val iterations: Int
)