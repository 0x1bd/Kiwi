package org.kvxd.kiwi.path

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

const val MAX_TRACKED_PLACEMENTS = 7

class PathNode(
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val z: Int,
    @JvmField var feetY: Double,
    @JvmField var kind: MoveKind,
    @JvmField var breaks: LongArray,
    @JvmField var place: Long,
    @JvmField var placements: Int,
    @JvmField var broken: Int,
    @JvmField var g: Double,
    @JvmField var h: Double,
    @JvmField var parent: PathNode?
) {
    @JvmField
    var heapIndex: Int = -1

    val f: Double get() = g + h

    val cell: Long get() = BlockPos.asLong(x, y, z)

    fun pos(): BlockPos = BlockPos(x, y, z)

    fun center(): Vec3 = Vec3(x + 0.5, feetY, z + 0.5)

    override fun toString(): String = "$kind@[$x,$y,$z]feet=$feetY"
}

class MoveBuffer(initialCapacity: Int = 64) {

    var size: Int = 0
    private set

    var x = IntArray(initialCapacity)
    private set
    var y = IntArray(initialCapacity)
    private set
    var z = IntArray(initialCapacity)
    private set
    var feetY = DoubleArray(initialCapacity)
    private set
    var cost = DoubleArray(initialCapacity)
    private set
    var kind = arrayOfNulls<MoveKind>(initialCapacity)
    private set
    var breaks = arrayOfNulls<LongArray>(initialCapacity)
    private set
    var place = LongArray(initialCapacity)
    private set

    fun clear() {
        size = 0
    }

    fun add(
        x: Int,
        y: Int,
        z: Int,
        feetY: Double,
        cost: Double,
        kind: MoveKind,
        breaks: LongArray = NO_BREAKS,
        place: Long = NO_PLACE
    ) {
        ensure(size + 1)
        this.x[size] = x
        this.y[size] = y
        this.z[size] = z
        this.feetY[size] = feetY
        this.cost[size] = cost
        this.kind[size] = kind
        this.breaks[size] = breaks
        this.place[size] = place
        size++
    }

    private fun ensure(capacity: Int) {
        if (capacity <= x.size) return
        val next = maxOf(capacity, x.size * 2)
        x = x.copyOf(next)
        y = y.copyOf(next)
        z = z.copyOf(next)
        feetY = feetY.copyOf(next)
        cost = cost.copyOf(next)
        kind = kind.copyOf(next)
        breaks = breaks.copyOf(next)
        place = place.copyOf(next)
    }
}
