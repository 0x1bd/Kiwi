package org.kvxd.kiwi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.kvxd.kiwi.path.MoveKind
import org.kvxd.kiwi.path.NO_BREAKS
import org.kvxd.kiwi.path.NO_PLACE
import org.kvxd.kiwi.path.NodeHeap
import org.kvxd.kiwi.path.PathNode
import kotlin.random.Random

class NodeHeapTest {

    private fun node(f: Double) = PathNode(0, 0, 0, 0.0, MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, f, 0.0, null)

    @Test
    fun `polls in ascending cost order`() {
        val heap = NodeHeap(4)
        val costs = listOf(5.0, 1.0, 4.0, 2.0, 3.0)
        costs.forEach { heap.add(node(it)) }

        val polled = generateSequence { heap.poll() }.map { it.f }.toList()
        assertEquals(costs.sorted(), polled)
        assertTrue(heap.isEmpty())
        assertNull(heap.poll())
    }

    @Test
    fun `decreasing a key reorders the heap`() {
        val heap = NodeHeap()
        val cheap = node(10.0)
        heap.add(node(1.0))
        heap.add(cheap)
        heap.add(node(2.0))

        cheap.g = 0.0
        heap.update(cheap)

        assertEquals(0.0, heap.poll()!!.f)
        assertEquals(1.0, heap.poll()!!.f)
        assertEquals(2.0, heap.poll()!!.f)
    }

    @Test
    fun `stays consistent under random churn`() {
        val random = Random(1234)
        val heap = NodeHeap(8)
        val expected = ArrayList<Double>()

        repeat(2000) {
            if (expected.isEmpty() || random.nextBoolean()) {
                val cost = random.nextDouble(0.0, 1000.0)
                heap.add(node(cost))
                expected.add(cost)
            } else {
                val smallest = expected.min()
                expected.remove(smallest)
                assertEquals(smallest, heap.poll()!!.f)
            }
        }

        expected.sort()
        for (cost in expected) assertEquals(cost, heap.poll()!!.f)
        assertTrue(heap.isEmpty())
    }
}
