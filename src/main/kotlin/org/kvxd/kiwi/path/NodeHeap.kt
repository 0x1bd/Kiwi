package org.kvxd.kiwi.path

class NodeHeap(initialCapacity: Int = 1024) {

    private var heap = arrayOfNulls<PathNode>(initialCapacity)
    private var count = 0

    val size: Int get() = count

    fun isEmpty(): Boolean = count == 0

    fun clear() {
        for (i in 0 until count) {
            heap[i]?.heapIndex = -1
            heap[i] = null
        }
        count = 0
    }

    fun add(node: PathNode) {
        if (count == heap.size) heap = heap.copyOf(heap.size * 2)
        heap[count] = node
        node.heapIndex = count
        siftUp(count)
        count++
    }

    fun poll(): PathNode? {
        if (count == 0) return null
        val root = heap[0]!!
        count--
        val last = heap[count]!!
        heap[count] = null
        root.heapIndex = -1
        if (count > 0) {
            heap[0] = last
            last.heapIndex = 0
            siftDown(0)
        }
        return root
    }

    fun update(node: PathNode) {
        val index = node.heapIndex
        if (index < 0) {
            add(node)
            return
        }
        siftUp(index)
        siftDown(node.heapIndex)
    }

    private fun siftUp(startIndex: Int) {
        var index = startIndex
        val node = heap[index]!!
        val key = node.f
        while (index > 0) {
            val parentIndex = (index - 1) ushr 1
            val parent = heap[parentIndex]!!
            if (key >= parent.f) break
            heap[index] = parent
            parent.heapIndex = index
            index = parentIndex
        }
        heap[index] = node
        node.heapIndex = index
    }

    private fun siftDown(startIndex: Int) {
        var index = startIndex
        val node = heap[index]!!
        val key = node.f
        val half = count ushr 1
        while (index < half) {
            var childIndex = (index shl 1) + 1
            var child = heap[childIndex]!!
            val rightIndex = childIndex + 1
            if (rightIndex < count) {
                val right = heap[rightIndex]!!
                if (right.f < child.f) {
                    childIndex = rightIndex
                    child = right
                }
            }
            if (key <= child.f) break
            heap[index] = child
            child.heapIndex = index
            index = childIndex
        }
        heap[index] = node
        node.heapIndex = index
    }
}
