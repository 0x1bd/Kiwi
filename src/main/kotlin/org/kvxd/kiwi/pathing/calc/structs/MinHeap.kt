package org.kvxd.kiwi.pathing.calc.structs

import org.kvxd.kiwi.pathing.calc.Node
import java.util.*

class MinHeap {

    private var heap = arrayOfNulls<Node>(1024)
    private var size = 0

    fun add(node: Node) {
        if (size >= heap.size) {
            heap = Arrays.copyOf(heap, heap.size * 2)
        }
        heap[size] = node
        node.heapIndex = size
        swim(size)
        size++
    }

    fun poll(): Node? {
        if (size == 0) return null
        val root = heap[0]
        val last = heap[size - 1]
        heap[0] = last
        if (last != null) last.heapIndex = 0
        heap[size - 1] = null
        size--
        if (size > 0) sink(0)
        return root
    }

    fun update(node: Node) {
        if (node.heapIndex != -1) {
            swim(node.heapIndex)
        }
    }

    fun isEmpty() = size == 0
    fun clear() {
        size = 0
    }

    private fun swim(k: Int) {
        var index = k
        while (index > 0) {
            val parent = (index - 1) ushr 1
            if (heap[index]!! < heap[parent]!!) {
                swap(index, parent)
                index = parent
            } else break
        }
    }

    private fun sink(k: Int) {
        var index = k
        val half = size ushr 1
        while (index < half) {
            var child = (index shl 1) + 1
            if (child + 1 < size && heap[child + 1]!! < heap[child]!!) {
                child++
            }
            if (heap[index]!! < heap[child]!!) break
            swap(index, child)
            index = child
        }
    }

    private fun swap(i: Int, j: Int) {
        val t = heap[i]
        heap[i] = heap[j]
        heap[j] = t
        heap[i]?.heapIndex = i
        heap[j]?.heapIndex = j
    }
}