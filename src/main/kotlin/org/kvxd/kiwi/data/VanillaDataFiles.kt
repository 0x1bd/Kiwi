package org.kvxd.kiwi.data

import net.fabricmc.loader.api.FabricLoader
import org.kvxd.kiwi.Kiwi
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator

object VanillaDataFiles {

    private const val MARKER_FILE = ".minecraft-version"

    private val logger = Kiwi.logger

    private val dataSets = listOf(
        DataSet("recipes", "data/minecraft/recipe"),
        DataSet("tags/items", "data/minecraft/tags/item"),
        DataSet("tags/blocks", "data/minecraft/tags/block"),
        DataSet("loot_tables/blocks", "data/minecraft/loot_table/blocks")
    )

    private var prepared = false

    fun jsonInputs(relativePath: String): List<Pair<String, InputStream>> {
        val root = prepare()
        val dir = root.resolve(relativePath)
        if (!Files.isDirectory(dir)) return emptyList()

        return Files.walk(dir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .map { path -> dir.relativize(path).toString().replace('\\', '/') to Files.newInputStream(path) }
                .toList()
        }
    }

    @Synchronized
    private fun prepare(): Path {
        val root = rootDirectory()
        if (!prepared || needsRefresh(root)) {
            extract(root)
            prepared = true
        }
        return root
    }

    private fun needsRefresh(root: Path): Boolean {
        val marker = root.resolve(MARKER_FILE)
        if (!Files.exists(marker)) return true
        if (Files.readString(marker) != minecraftVersion()) return true
        return dataSets.any { !Files.isDirectory(root.resolve(it.targetPath)) }
    }

    private fun extract(root: Path) {
        if (Files.exists(root)) {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .filter { it != root }
                    .forEach(Files::deleteIfExists)
            }
        }
        Files.createDirectories(root)

        val classLoader = VanillaDataFiles::class.java.classLoader
        for (dataSet in dataSets) {
            val target = root.resolve(dataSet.targetPath)
            Files.createDirectories(target)
            val count = copyResources(classLoader, dataSet.sourcePath, target)
            logger.info("Kiwi vanilla data: extracted $count files from ${dataSet.sourcePath}")
        }

        Files.writeString(root.resolve(MARKER_FILE), minecraftVersion())
    }

    private fun copyResources(classLoader: ClassLoader, sourcePath: String, target: Path): Int {
        var count = 0
        val urls = classLoader.getResources(sourcePath).toList()

        for (url in urls) {
            when (url.protocol) {
                "file" -> {
                    val dir = runCatching { Paths.get(url.toURI()) }.getOrNull() ?: continue
                    if (!Files.isDirectory(dir)) continue
                    Files.walk(dir).use { stream ->
                        stream
                            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                            .forEach { source ->
                                val relative = dir.relativize(source)
                                val output = target.resolve(relative)
                                Files.createDirectories(output.parent)
                                Files.copy(source, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                count++
                            }
                    }
                }
                "jar" -> {
                    val connection = runCatching { url.openConnection() as? JarURLConnection }.getOrNull() ?: continue
                    val jarFile = connection.jarFile
                    val prefix = if (sourcePath.endsWith("/")) sourcePath else "$sourcePath/"
                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        if (entry.isDirectory || !entryName.startsWith(prefix) || !entryName.endsWith(".json")) continue

                        val relativeName = entryName.removePrefix(prefix)
                        val output = target.resolve(relativeName)
                        Files.createDirectories(output.parent)
                        jarFile.getInputStream(entry).use { input ->
                            Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                        count++
                    }
                }
            }
        }

        return count
    }

    private fun rootDirectory(): Path {
        return runCatching {
            val gameDir = FabricLoader.getInstance().gameDir
            val cacheParent = if (isProjectRoot(gameDir)) gameDir.resolve("run") else gameDir
            cacheParent.resolve(Kiwi.MOD_ID).resolve("vanilla-data")
        }.getOrElse {
            Paths.get("run").resolve(Kiwi.MOD_ID).resolve("vanilla-data")
        }
    }

    private fun isProjectRoot(path: Path): Boolean {
        return Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
            Files.isRegularFile(path.resolve("gradle.properties")) &&
            Files.isDirectory(path.resolve("src"))
    }

    private fun minecraftVersion(): String {
        return runCatching {
            FabricLoader.getInstance().getModContainer("minecraft")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
        }.getOrDefault("unknown")
    }

    private data class DataSet(
        val targetPath: String,
        val sourcePath: String
    )
}
