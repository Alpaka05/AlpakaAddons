plugins {
    id("fabric-loom") version "1.13.6"
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("mod_id") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.terraformersmc.com/")
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")

    // Fabric Loader & API
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("net.fabricmc.fabric-api:fabric-command-api-v2:3.0.5+e2bdee784c")

    // Mixin compile dependency
    compileOnly("org.spongepowered:mixin:0.8.5")

    // Mod Menu compile dependency
    compileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val oneClientDir = file("${System.getProperty("user.home")}/Library/Application Support/org.Polyfrost.OneClient")
val minecraftModsDir = file("${System.getProperty("user.home")}/Library/Application Support/org.Polyfrost.OneClient/.minecraft/mods")
val fabricProcessedDir = file("${System.getProperty("user.home")}/Library/Application Support/org.Polyfrost.OneClient/.minecraft/.fabric/processedMods")

tasks.named("runClient") {
    setDependsOn(listOf(tasks.jar.get()))
    actions.clear()
    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        val targetName = "alpaka-${project.version}.jar"

        // 1. Copy to .minecraft/mods where OneClient auto-detects new/updated mods
        minecraftModsDir.mkdirs()
        minecraftModsDir.listFiles()?.filter { it.name.startsWith("alpaka") }?.forEach { it.delete() }
        jarFile.copyTo(File(minecraftModsDir, targetName), overwrite = true)
        println("Copied ${targetName} to .minecraft/mods")

        // 2. Clear Fabric Loader's processedMods cache for alpaka
        if (fabricProcessedDir.exists()) {
            fabricProcessedDir.listFiles()?.filter { it.name.startsWith("alpaka") }?.forEach {
                it.delete()
                println("Deleted Fabric processedMod cache: ${it.name}")
            }
        }

        // 3. Update all existing alpaka jar files in OneClient launcher caches
        if (oneClientDir.exists()) {
            oneClientDir.walkTopDown().filter { it.isFile && it.name.startsWith("alpaka") && it.name.endsWith(".jar") }.forEach { targetFile ->
                jarFile.copyTo(targetFile, overwrite = true)
                println("Updated OneClient mod file: ${targetFile.absolutePath}")
            }
        }

        // 4. Launch OneClient app
        ProcessBuilder("open", "-a", "/Applications/OneClient.app").start()
    }
}
