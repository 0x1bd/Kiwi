package org.kvxd.kiwi.harvest

import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.kvxd.kiwi.level
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

        var count = 0
        var errors = 0

        try {
            for (block in BuiltInRegistries.BLOCK) {
                try {
                    val blockId = BuiltInRegistries.BLOCK.getKey(block).path

                    val toolType = detectToolType(block)
                    val minTier = detectToolTier(block)

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

    private fun detectToolType(block: net.minecraft.world.level.block.Block): HarvestToolType {
        val blockState = block.defaultBlockState()
        return try {
            when {
                blockState.`is`(BlockTags.MINEABLE_WITH_PICKAXE) -> HarvestToolType.PICKAXE
                blockState.`is`(BlockTags.MINEABLE_WITH_AXE) -> HarvestToolType.AXE
                blockState.`is`(BlockTags.MINEABLE_WITH_SHOVEL) -> HarvestToolType.SHOVEL
                blockState.`is`(BlockTags.MINEABLE_WITH_HOE) -> HarvestToolType.HOE
                blockState.`is`(BlockTags.SWORD_EFFICIENT) -> HarvestToolType.SWORD
                else -> detectToolTypeFallback(block)
            }
        } catch (_: Exception) {
            detectToolTypeFallback(block)
        }
    }

    private fun detectToolTypeFallback(block: net.minecraft.world.level.block.Block): HarvestToolType {
        val name = block.javaClass.name.lowercase()
        return when {
            "ore" in name || "stone" in name || "rock" in name || "obsidian" in name -> HarvestToolType.PICKAXE
            "log" in name || "wood" in name || "plank" in name || "stem" in name -> HarvestToolType.AXE
            "dirt" in name || "sand" in name || "gravel" in name || "clay" in name || "snow" in name -> HarvestToolType.SHOVEL
            "leaves" in name || "wool" in name || "web" in name -> HarvestToolType.SHEARS
            else -> HarvestToolType.ANY
        }
    }

    private fun detectToolTier(block: net.minecraft.world.level.block.Block): HarvestToolTier {
        val blockState = block.defaultBlockState()
        return try {
            when {
                blockState.`is`(BlockTags.NEEDS_DIAMOND_TOOL) -> HarvestToolTier.DIAMOND
                blockState.`is`(BlockTags.NEEDS_IRON_TOOL) -> HarvestToolTier.IRON
                blockState.`is`(BlockTags.NEEDS_STONE_TOOL) -> HarvestToolTier.STONE
                else -> detectToolTierFallback(block)
            }
        } catch (_: Exception) {
            detectToolTierFallback(block)
        }
    }

    private fun detectToolTierFallback(block: net.minecraft.world.level.block.Block): HarvestToolTier {
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
        val inputs = collectResources("data/kiwi/loot_tables/vanilla/blocks", ".json")

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
                try { stream.close() } catch (_: Exception) {}
            }
        }

        logger.info("Parsed ${drops.size} loot table drops")
        return drops
    }

    private fun collectResources(basePath: String, extension: String): List<Pair<String, InputStream>> {
        val result = mutableListOf<Pair<String, InputStream>>()
        val classLoader = HarvestDatabase::class.java.classLoader
        val urls = classLoader.getResources(basePath).toList()

        for (url in urls) {
            when (url.protocol) {
                "file" -> {
                    val dir = try { java.nio.file.Paths.get(url.toURI()) } catch (_: Exception) { continue }
                    if (java.nio.file.Files.isDirectory(dir)) {
                        java.nio.file.Files.list(dir).use { stream ->
                            stream.filter { it.fileName.toString().endsWith(extension) }
                                .forEach { path ->
                                    val name = path.fileName.toString()
                                    result.add(name to java.nio.file.Files.newInputStream(path))
                                }
                        }
                    }
                }
                "jar" -> {
                    try {
                        val connection = url.openConnection() as? java.net.JarURLConnection ?: continue
                        val jarFile = connection.jarFile
                        val entries = jarFile.entries()
                        val prefix = if (basePath.endsWith("/")) basePath else "$basePath/"
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val entryName = entry.name
                            if (!entry.isDirectory && entryName.startsWith(prefix) && entryName.endsWith(extension)) {
                                val name = entryName.substringAfterLast("/")
                                result.add(name to jarFile.getInputStream(entry))
                            }
                        }
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
        return result
    }
}
