package org.kvxd.kiwi.recipe

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class TagResolverTest {

    private fun tagStream(json: String): InputStream =
        ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

    @BeforeEach
    fun reset() {
        TagResolver.load(emptyList())
    }

    @Test
    fun `resolves simple item tag`() {
        val stream = tagStream("""{ "values": ["minecraft:stone", "minecraft:cobblestone"] }""")
        TagResolver.load(listOf("test_tag.json" to stream))

        val items = TagResolver.getItems("minecraft:test_tag")
        assertTrue(items.contains("minecraft:stone"))
        assertTrue(items.contains("minecraft:cobblestone"))
        assertEquals(2, items.size)
    }

    @Test
    fun `resolves tag with short name`() {
        val stream = tagStream("""{ "values": ["minecraft:dirt"] }""")
        TagResolver.load(listOf("dirt_like.json" to stream))

        val items = TagResolver.getItems("dirt_like")
        assertTrue(items.contains("minecraft:dirt"))
    }

    @Test
    fun `resolves nested tag references`() {
        val inner = tagStream("""{ "values": ["minecraft:oak_planks", "minecraft:birch_planks"] }""")
        val outer = tagStream("""{ "values": ["#minecraft:planks", "minecraft:stone"] }""")

        TagResolver.load(listOf("planks.json" to inner, "building_blocks.json" to outer))

        val items = TagResolver.getItems("minecraft:building_blocks")
        assertTrue(items.contains("minecraft:stone"))
        assertTrue(items.contains("minecraft:oak_planks"))
        assertTrue(items.contains("minecraft:birch_planks"))
    }

    @Test
    fun `resolves tag with hash prefix`() {
        val stream = tagStream("""{ "values": ["minecraft:iron_ingot"] }""")
        TagResolver.load(listOf("iron_materials.json" to stream))

        val items = TagResolver.getItems("#minecraft:iron_materials")
        assertTrue(items.contains("minecraft:iron_ingot"))
    }

    @Test
    fun `returns empty set for unknown tag`() {
        val items = TagResolver.getItems("minecraft:nonexistent_tag")
       	assertTrue(items.isEmpty())
    }

    @Test
    fun `hasItem checks membership`() {
        val stream = tagStream("""{ "values": ["minecraft:diamond"] }""")
        TagResolver.load(listOf("gem_items.json" to stream))

        assertTrue(TagResolver.hasItem("minecraft:gem_items", "minecraft:diamond"))
        assertFalse(TagResolver.hasItem("minecraft:gem_items", "minecraft:emerald"))
    }

    @Test
    fun `handles empty tag`() {
        val stream = tagStream("""{ "values": [] }""")
        TagResolver.load(listOf("empty_tag.json" to stream))

        val items = TagResolver.getItems("minecraft:empty_tag")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `parses tag json with object entries`() {
        val json = """{ "values": [{"id": "minecraft:stone", "required": true}, "minecraft:cobblestone"] }"""
        val stream = tagStream(json)
        TagResolver.load(listOf("stone_materials.json" to stream))

        val items = TagResolver.getItems("minecraft:stone_materials")
        assertTrue(items.contains("minecraft:stone"))
        assertTrue(items.contains("minecraft:cobblestone"))
    }

    @Test
    fun `isLoaded reflects state`() {
        assertFalse(TagResolver.isLoaded)
        val stream = tagStream("""{ "values": ["minecraft:test"] }""")
        TagResolver.load(listOf("test.json" to stream))
        assertTrue(TagResolver.isLoaded)
    }
}