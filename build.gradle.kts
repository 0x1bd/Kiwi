import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.3.21"
    id("fabric-loom") version "1.13-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {

}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
}

val extractVanillaData = tasks.register("extractVanillaData") {
    group = "kiwi"
    description = "Extracts vanilla recipe, tag, and loot table JSONs from the Minecraft jar into mod resources"

    inputs.property("minecraft_version", project.property("minecraft_version"))

    val recipeDir = layout.projectDirectory.dir("src/main/resources/data/kiwi/recipes/vanilla")
    val tagDir = layout.projectDirectory.dir("src/main/resources/data/kiwi/tags/vanilla/items")
    val lootDir = layout.projectDirectory.dir("src/main/resources/data/kiwi/loot_tables/vanilla/blocks")

    outputs.dirs(recipeDir, tagDir, lootDir)

    doLast {
        val runtimeClasspath = configurations["runtimeClasspath"]
        val minecraftJar = runtimeClasspath.resolvedConfiguration.resolvedArtifacts
            .find { it.moduleVersion.id.group == "net.minecraft" && it.extension == "jar" }?.file
            ?: runtimeClasspath.resolvedConfiguration.resolvedArtifacts
                .find { it.name.contains("minecraft-merged") && it.extension == "jar" }?.file
            ?: throw GradleException("Could not find Minecraft jar in runtime classpath")

        logger.lifecycle("Extracting from: ${minecraftJar.absolutePath}")

        ZipFile(minecraftJar).use { zip ->
            val recipeEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("data/minecraft/recipe/") && it.name.endsWith(".json") }
                .toList()
            val tagEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("data/minecraft/tags/item/") && it.name.endsWith(".json") }
                .toList()
            val lootEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("data/minecraft/loot_table/blocks/") && it.name.endsWith(".json") }
                .toList()

            recipeDir.asFile.mkdirs()
            tagDir.asFile.mkdirs()
            lootDir.asFile.mkdirs()

            for (entry in recipeEntries) {
                val fileName = entry.name.substringAfterLast("/")
                recipeDir.file(fileName).asFile.outputStream().use { out ->
                    zip.getInputStream(entry).copyTo(out)
                }
            }

            for (entry in tagEntries) {
                val fileName = entry.name.substringAfterLast("/")
                tagDir.file(fileName).asFile.outputStream().use { out ->
                    zip.getInputStream(entry).copyTo(out)
                }
            }

            for (entry in lootEntries) {
                val fileName = entry.name.substringAfterLast("/")
                lootDir.file(fileName).asFile.outputStream().use { out ->
                    zip.getInputStream(entry).copyTo(out)
                }
            }

            logger.lifecycle("Extracted ${recipeEntries.size} recipes, ${tagEntries.size} tags, ${lootEntries.size} loot tables")
        }
    }
}

tasks.processResources {
    dependsOn(extractVanillaData)
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version")
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    dependsOn(extractVanillaData)
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.named("sourcesJar").configure {
    dependsOn(extractVanillaData)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {

    }
}
