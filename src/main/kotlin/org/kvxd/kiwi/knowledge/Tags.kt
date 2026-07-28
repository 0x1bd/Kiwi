package org.kvxd.kiwi.knowledge

import com.google.gson.JsonParser
import java.io.InputStream
import java.io.InputStreamReader

class TagTable private constructor(
    private val direct: Map<String, Set<String>>,
    private val references: Map<String, Set<String>>
) {

    private val resolved = HashMap<String, Set<String>>()

    val size: Int get() = direct.size

    fun members(tagId: String): Set<String> {
        val key = Ids.normalize(tagId)
        resolved[key]?.let { return it }
        val computed = resolve(key, HashSet())
        resolved[key] = computed
        return computed
    }

    fun contains(tagId: String, entryId: String): Boolean = Ids.normalize(entryId) in members(tagId)

    private fun resolve(tagId: String, visited: MutableSet<String>): Set<String> {
        if (!visited.add(tagId)) return emptySet()
        val result = HashSet(direct[tagId].orEmpty())
        for (reference in references[tagId].orEmpty()) {
            result.addAll(resolve(reference, visited))
        }
        return result
    }

    companion object {
        val EMPTY = TagTable(emptyMap(), emptyMap())

        fun parse(inputs: List<Pair<String, InputStream>>): TagTable {
            val direct = HashMap<String, MutableSet<String>>()
            val references = HashMap<String, MutableSet<String>>()

            for ((filename, stream) in inputs) {
                try {
                    val tagId = Ids.normalize(filename.removeSuffix(".json"))
                    val entries = direct.getOrPut(tagId) { HashSet() }
                    val refs = references.getOrPut(tagId) { HashSet() }
                    read(stream, entries, refs)
                } catch (_: Exception) {
                } finally {
                    runCatching { stream.close() }
                }
            }
            return TagTable(direct, references)
        }

        private fun read(stream: InputStream, entries: MutableSet<String>, references: MutableSet<String>) {
            val json = InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it) }
            if (!json.isJsonObject) return
            val values = json.asJsonObject.getAsJsonArray("values") ?: return

            for (element in values) {
                val id = when {
                    element.isJsonPrimitive -> element.asString
                    element.isJsonObject -> element.asJsonObject.get("id")?.asString ?: continue
                    else -> continue
                }
                if (id.startsWith("#")) references.add(Ids.normalize(id)) else entries.add(Ids.normalize(id))
            }
        }
    }
}
