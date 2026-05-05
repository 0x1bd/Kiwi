package org.kvxd.kiwi.recipe

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecipeGraphTest {

    private lateinit var graph: RecipeGraph

    @BeforeEach
    fun setUp() {
        if (!RecipeDatabase.isLoaded) {
            RecipeDatabase.load()
        }
        graph = RecipeGraph()
        graph.build()
    }

    @Test
    fun `graph builds without error`() {
        assertNotNull(graph)
    }

    @Test
    fun `findPathTo returns empty list when item already available`() {
        val available = mapOf("stick" to 5)
        val path = graph.findPathTo("stick", available)
        assertNotNull(path)
        assertTrue(path!!.isEmpty(), "Should return empty path when item is already available")
    }

    @Test
    fun `findPathTo returns null for uncraftable item`() {
        val available = emptyMap<String, Int>()
        val path = graph.findPathTo("nonexistent_item_xyzzy", available)
        assertNull(path, "Should return null for items with no recipe")
    }

    @Test
    fun `findPathTo finds path for craftable item`() {
        val available = mapOf(
            "oak_planks" to 10,
            "stick" to 10
        )
        val path = graph.findPathTo("crafting_table", available)
        assertNotNull(path, "Should find a path to crafting_table from available ingredients")
    }

    @Test
    fun `findAllDependencies returns recipe set`() {
        val available = mapOf("oak_planks" to 10, "stick" to 10)
        val deps = graph.findAllDependencies("crafting_table", available)
        assertNotNull(deps)
    }
}