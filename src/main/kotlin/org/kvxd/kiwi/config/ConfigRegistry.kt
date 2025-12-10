package org.kvxd.kiwi.config

object ConfigRegistry {

    private val entries = mutableMapOf<String, ConfigEntry<*>>()
    private var dirty = false

    fun register(entry: ConfigEntry<*>) {
        entries[entry.key] = entry
    }

    fun getEntries(): Map<String, ConfigEntry<*>> = entries

    fun markDirty() {
        dirty = true
    }

    fun isDirty(): Boolean = dirty
    fun clearDirty() {
        dirty = false
    }
}
