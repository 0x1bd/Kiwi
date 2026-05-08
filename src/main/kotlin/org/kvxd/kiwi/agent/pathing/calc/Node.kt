package org.kvxd.kiwi.agent.pathing.calc

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

data class Node(
    val pos: BlockPos,
    var parent: Node?,
    var costG: Double,
    var costH: Double,
    var action: MovementAction,
    var pillarBlocks: Int = 0,
    var heapIndex: Int = -1
) : Comparable<Node> {

    constructor(
        pos: BlockPos,
        parent: Node?,
        costG: Double,
        costH: Double,
        type: MovementType,
        miningBlocks: List<BlockPos> = emptyList(),
        miningCost: Double = 0.0,
        pillarBlocks: Int = 0,
        heapIndex: Int = -1
    ) : this(
        pos = pos,
        parent = parent,
        costG = costG,
        costH = costH,
        action = PlannedMovementAction(type, pos, miningBlocks, miningCost),
        pillarBlocks = pillarBlocks,
        heapIndex = heapIndex
    )

    val costF: Double get() = costG + costH

    val posLong: Long = pos.asLong()
    val stateKey: NodeStateKey get() = NodeStateKey(posLong, type, pillarBlocks.coerceAtMost(MAX_TRACKED_PILLAR_BLOCKS))

    var type: MovementType
        get() = action.type
        set(value) {
            action = action.withType(value)
        }

    val miningBlocks: List<BlockPos>
        get() = action.miningBlocks

    val miningCost: Double
        get() = action.miningCost

    override fun compareTo(other: Node): Int {
        return costF.compareTo(other.costF)
    }

    fun toVec(): Vec3 = Vec3.atBottomCenterOf(pos)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Node
        return stateKey == other.stateKey
    }

    override fun hashCode(): Int {
        return stateKey.hashCode()
    }
}

data class NodeStateKey(
    val posLong: Long,
    val type: MovementType,
    val pillarBlocks: Int
)

const val MAX_TRACKED_PILLAR_BLOCKS = 64
