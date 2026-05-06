package org.kvxd.kiwi.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block

val Block.registryPath: String
    get() = BuiltInRegistries.BLOCK.getKey(this).path

fun resolveBlockId(id: String): Block? {
    val fullId = if (id.contains(":")) id else "minecraft:$id"
    return runCatching {
        BuiltInRegistries.BLOCK.getOptional(Identifier.parse(fullId)).orElse(null)
    }.getOrNull()
}
