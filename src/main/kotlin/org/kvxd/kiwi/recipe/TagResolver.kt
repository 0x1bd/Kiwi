package org.kvxd.kiwi.recipe

import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader

object TagResolver {

    private val logger = LoggerFactory.getLogger("kiwi:TagResolver")

    private val tagToItems = mutableMapOf<String, Set<String>>()
    private val tagToRefs = mutableMapOf<String, Set<String>>()
    private val resolvedCache = mutableMapOf<String, Set<String>>()

    val isLoaded: Boolean get() = tagToItems.isNotEmpty()

    fun load(inputs: List<Pair<String, InputStream>>) {
        tagToItems.clear()
        tagToRefs.clear()
        resolvedCache.clear()
        for ((filename, stream) in inputs) {
            try {
                val (items, refs) = parseTagJson(stream)
                val tagName = filename.removeSuffix(".json")
                val tagId = "minecraft:$tagName"
                tagToItems[tagId] = items
                tagToRefs[tagId] = refs
                tagToItems[tagName] = items
                tagToRefs[tagName] = refs
            } catch (e: Exception) {
                logger.warn("Failed to parse tag $filename: ${e.message}")
            } finally {
                try { stream.close() } catch (_: Exception) {}
            }
        }
        logger.info("Loaded ${tagToItems.size / 2} item tags")
    }

    fun resolve(tagId: String): Set<String> {
        val clean = tagId.removePrefix("#")
        return resolvedCache.getOrPut(clean) {
            resolveRecursive(clean, mutableSetOf())
        }
    }

    private fun resolveRecursive(tagId: String, visited: MutableSet<String>): Set<String> {
        val clean = tagId.removePrefix("#")
        if (clean in visited) return emptySet()
        visited.add(clean)

        val items = lookupItems(clean)
        val refs = lookupRefs(clean)

        if (refs.isEmpty()) return items

        val result = items.toMutableSet()
        for (ref in refs) {
            result.addAll(resolveRecursive(ref, visited))
        }
        return result
    }

    private fun lookupItems(tagId: String): Set<String> {
        return tagToItems[tagId]
            ?: tagToItems[tagId.replace(":", "/")]
            ?: tagToItems[tagId.substringAfterLast(":")]
            ?: tagToItems[tagId.substringAfterLast("/")]
            ?: emptySet()
    }

    private fun lookupRefs(tagId: String): Set<String> {
        return tagToRefs[tagId]
            ?: tagToRefs[tagId.replace(":", "/")]
            ?: tagToRefs[tagId.substringAfterLast(":")]
            ?: tagToRefs[tagId.substringAfterLast("/")]
            ?: emptySet()
    }

    fun getItems(tagId: String): Set<String> = resolve(tagId)

    fun hasItem(tagId: String, itemId: String): Boolean = resolve(tagId).contains(itemId)

    private fun parseTagJson(stream: InputStream): Pair<Set<String>, Set<String>> {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val json = JsonParser.parseReader(reader).asJsonObject
        reader.close()

        val values = json?.getAsJsonArray("values") ?: return Pair(emptySet(), emptySet())
        val items = mutableSetOf<String>()
        val refs = mutableSetOf<String>()

        for (elem in values) {
            val id = if (elem.isJsonPrimitive) {
                elem.asString
            } else if (elem.isJsonObject) {
                elem.asJsonObject.get("id")?.asString ?: continue
            } else {
                continue
            }

            if (id.startsWith("#")) {
                refs.add(id.removePrefix("#"))
            } else {
                items.add(id)
            }
        }

        return Pair(items, refs)
    }
}
