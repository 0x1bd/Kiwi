package org.kvxd.kiwi.knowledge

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

const val NO_ID = -1

object Ids {

    val itemCount: Int get() = BuiltInRegistries.ITEM.size()

    val blockCount: Int get() = BuiltInRegistries.BLOCK.size()

    fun item(id: String): Int {
        val identifier = parse(id) ?: return NO_ID
        val item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null) ?: return NO_ID
        return BuiltInRegistries.ITEM.getId(item)
    }

    fun item(item: Item): Int = BuiltInRegistries.ITEM.getId(item)

    fun itemOf(stack: ItemStack): Int = if (stack.isEmpty) NO_ID else BuiltInRegistries.ITEM.getId(stack.item)

    fun itemById(id: Int): Item? = if (id < 0) null else BuiltInRegistries.ITEM.byId(id)

    fun itemName(id: Int): String = itemById(id)?.let { BuiltInRegistries.ITEM.getKey(it).path } ?: "?"

    fun block(id: String): Int {
        val identifier = parse(id) ?: return NO_ID
        val block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null) ?: return NO_ID
        return BuiltInRegistries.BLOCK.getId(block)
    }

    fun block(block: Block): Int = BuiltInRegistries.BLOCK.getId(block)

    fun blockById(id: Int): Block? = if (id < 0) null else BuiltInRegistries.BLOCK.byId(id)

    fun blockName(id: Int): String = blockById(id)?.let { BuiltInRegistries.BLOCK.getKey(it).path } ?: "?"

    fun normalize(id: String): String = id.removePrefix("#").let { if (it.contains(':')) it else "minecraft:$it" }

    private fun parse(id: String): Identifier? = runCatching { Identifier.parse(normalize(id)) }.getOrNull()
}

val Block.registryPath: String get() = BuiltInRegistries.BLOCK.getKey(this).path

val Item.registryPath: String get() = BuiltInRegistries.ITEM.getKey(this).path
