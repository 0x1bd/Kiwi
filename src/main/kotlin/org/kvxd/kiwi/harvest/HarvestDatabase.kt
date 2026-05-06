package org.kvxd.kiwi.harvest

import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import org.kvxd.kiwi.data.VanillaDataFiles
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader

object HarvestDatabase {

    private val logger = LoggerFactory.getLogger("kiwi:HarvestDatabase")

    private val byBlock = mutableMapOf<String, BlockHarvestInfo>()
    private val byDrop = mutableMapOf<String, MutableList<BlockHarvestInfo>>()

    val isLoaded: Boolean get() = byBlock.isNotEmpty()

    val allBlocks: Collection<BlockHarvestInfo> get() = byBlock.values.distinctBy { it.blockId }

    fun load() {
        byBlock.clear()
        byDrop.clear()

        logger.info("HarvestDatabase: starting load...")

        val dropsOverrides = buildDropOverrides()
        val lootTableDrops = parseLootTables()
        val blockTags = VanillaBlockTags.load()

        var count = 0
        var errors = 0

        try {
            for (block in BuiltInRegistries.BLOCK) {
                try {
                    val blockId = BuiltInRegistries.BLOCK.getKey(block).path

                    val toolType = detectToolType(blockId, blockTags)
                    val minTier = detectToolTier(blockId, block, blockTags)

                    val requiresCorrectTool = try {
                        block.defaultBlockState().requiresCorrectToolForDrops()
                    } catch (_: Exception) {
                        minTier != HarvestToolTier.NONE
                    }

                    val fullBlockId = "minecraft:$blockId"
                    val dropId = lootTableDrops[fullBlockId]
                        ?: dropsOverrides[fullBlockId]
                        ?: blockId

                    val info = BlockHarvestInfo(
                        blockId = blockId,
                        primaryDropId = dropId,
                        dropCount = 1..1,
                        toolType = toolType,
                        minTier = minTier,
                        requiresCorrectTool = requiresCorrectTool
                    )

                    byBlock[blockId] = info
                    byBlock[fullBlockId] = info
                    byDrop.getOrPut(dropId) { mutableListOf() }.add(info)
                    count++
                } catch (e: Exception) {
                    errors++
                    if (errors <= 5) {
                        logger.warn("Failed to process block: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("HarvestDatabase iteration failed: ${e.message}", e)
        }

        if (errors > 0) logger.warn("$errors blocks failed to process")
        logger.info("HarvestDatabase loaded: $count blocks, ${byDrop.size} drop types, $errors errors")
    }

    private fun detectToolType(blockId: String, blockTags: VanillaBlockTags): HarvestToolType {
        return when {
            blockTags.contains("minecraft:mineable/pickaxe", blockId) -> HarvestToolType.PICKAXE
            blockTags.contains("minecraft:mineable/axe", blockId) -> HarvestToolType.AXE
            blockTags.contains("minecraft:mineable/shovel", blockId) -> HarvestToolType.SHOVEL
            blockTags.contains("minecraft:mineable/hoe", blockId) -> HarvestToolType.HOE
            blockTags.contains("minecraft:sword_efficient", blockId) -> HarvestToolType.SWORD
            else -> HarvestToolType.ANY
        }
    }

    private fun detectToolTier(blockId: String, block: Block, blockTags: VanillaBlockTags): HarvestToolTier {
        return when {
            blockTags.contains("minecraft:needs_diamond_tool", blockId) -> HarvestToolTier.DIAMOND
            blockTags.contains("minecraft:needs_iron_tool", blockId) -> HarvestToolTier.IRON
            blockTags.contains("minecraft:needs_stone_tool", blockId) -> HarvestToolTier.STONE
            else -> detectToolTierFallback(block)
        }
    }

    private fun detectToolTierFallback(block: Block): HarvestToolTier {
        val destroyTime = block.defaultDestroyTime()
        return when {
            destroyTime >= 50.0f -> HarvestToolTier.NETHERITE
            destroyTime >= 30.0f -> HarvestToolTier.DIAMOND
            destroyTime >= 5.0f -> HarvestToolTier.IRON
            destroyTime >= 3.0f -> HarvestToolTier.STONE
            else -> HarvestToolTier.WOOD
        }
    }

    fun getForBlock(blockId: String): BlockHarvestInfo? {
        val key = if (blockId.contains(":")) blockId.substringAfterLast(":") else blockId
        return byBlock[key] ?: byBlock[blockId]
    }

    fun getBlocksForDrop(dropId: String): List<BlockHarvestInfo> {
        val key = if (dropId.contains(":")) dropId.substringAfterLast(":") else dropId
        return byDrop[key] ?: byDrop[dropId] ?: emptyList()
    }

    fun findBlockAlternatives(dropId: String): List<String> {
        return getBlocksForDrop(dropId).map { it.blockId }.distinct()
    }

    fun bestToolForBlock(blockId: String): String? {
        return getForBlock(blockId)?.bestToolItemId()
    }

    private fun buildDropOverrides(): Map<String, String> {
        return emptyMap()
    }

    private fun parseLootTables(): Map<String, String> {
        val drops = mutableMapOf<String, String>()
        val inputs = VanillaDataFiles.jsonInputs("loot_tables/blocks")

        for ((filename, stream) in inputs) {
            try {
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val json = JsonParser.parseReader(reader).asJsonObject
                reader.close()

                val pools = json?.getAsJsonArray("pools") ?: continue
                if (pools.isEmpty) continue

                val firstPool = pools[0].asJsonObject ?: continue
                val entries = firstPool.getAsJsonArray("entries") ?: continue
                if (entries.isEmpty) continue

                val firstEntry = entries[0].asJsonObject ?: continue
                val type = firstEntry.get("type")?.asString ?: continue

                val name: String = when (type) {
                    "minecraft:item" -> firstEntry.get("name")?.asString ?: continue
                    "minecraft:alternatives" -> {
                        val children = firstEntry.getAsJsonArray("children")
                            ?: continue
                        if (children.isEmpty) continue
                        val lastChild = children[children.size() - 1].asJsonObject ?: continue
                        lastChild.get("name")?.asString ?: continue
                    }

                    else -> continue
                }

                val blockName = filename.removeSuffix(".json")
                drops["minecraft:$blockName"] = name.removePrefix("minecraft:")
            } catch (_: Exception) {
            } finally {
                try {
                    stream.close()
                } catch (_: Exception) {
                }
            }
        }

        logger.info("Parsed ${drops.size} loot table drops")
        return drops
    }

    private class VanillaBlockTags(
        private val directBlocks: Map<String, Set<String>>,
        private val tagRefs: Map<String, Set<String>>
    ) {
        fun contains(tagId: String, blockId: String): Boolean {
            return resolve(tagId, mutableSetOf()).contains(normalizeBlockId(blockId))
        }

        private fun resolve(tagId: String, visited: MutableSet<String>): Set<String> {
            val normalized = normalizeTagId(tagId)
            if (!visited.add(normalized)) return emptySet()

            val result = directBlocks[normalized].orEmpty().toMutableSet()
            for (ref in tagRefs[normalized].orEmpty()) {
                result.addAll(resolve(ref, visited))
            }
            return result
        }

        companion object {
            fun load(): VanillaBlockTags {
                val directBlocks = mutableMapOf<String, MutableSet<String>>()
                val tagRefs = mutableMapOf<String, MutableSet<String>>()

                for ((filename, stream) in VanillaDataFiles.jsonInputs("tags/blocks")) {
                    try {
                        val tagId = normalizeTagId(filename.removeSuffix(".json"))
                        val (blocks, refs) = parseTagJson(stream)
                        directBlocks.getOrPut(tagId) { mutableSetOf() }.addAll(blocks)
                        tagRefs.getOrPut(tagId) { mutableSetOf() }.addAll(refs)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse block tag $filename: ${e.message}")
                    } finally {
                        try {
                            stream.close()
                        } catch (_: Exception) {
                        }
                    }
                }

                logger.info("Loaded ${directBlocks.size} block tags")
                return VanillaBlockTags(directBlocks, tagRefs)
            }

            private fun parseTagJson(stream: InputStream): Pair<Set<String>, Set<String>> {
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val json = JsonParser.parseReader(reader).asJsonObject
                reader.close()

                val values = json?.getAsJsonArray("values") ?: return emptySet<String>() to emptySet()
                val blocks = mutableSetOf<String>()
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
                        refs.add(normalizeTagId(id.removePrefix("#")))
                    } else {
                        blocks.add(normalizeBlockId(id))
                    }
                }

                return blocks to refs
            }

            private fun normalizeTagId(tagId: String): String {
                return if (tagId.contains(":")) tagId else "minecraft:$tagId"
            }

            private fun normalizeBlockId(blockId: String): String {
                return if (blockId.contains(":")) blockId else "minecraft:$blockId"
            }
        }
    }

}
