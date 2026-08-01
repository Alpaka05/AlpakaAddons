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

val oneClientModsDir = file("${System.getProperty("user.home")}/Library/Application Support/org.Polyfrost.OneClient/clusters/26.1.2 Fabric/mods")
val oneClientImportedDir = file("${System.getProperty("user.home")}/Library/Application Support/org.Polyfrost.OneClient/metadata/packages/mods/local/imported/fc57d24811772224")

tasks.register("copyToLauncher") {
    dependsOn("jar")
    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        val targetName = "alpaka-${project.version}.jar"
        
        listOf(oneClientModsDir, oneClientImportedDir).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.filter { it.name.startsWith("alpaka") && it.name.endsWith(".jar") }?.forEach { oldJar ->
                    oldJar.delete()
                }
                jarFile.copyTo(File(dir, targetName), overwrite = true)
            }
        }
    }
}

tasks.build {
    finalizedBy("copyToLauncher")
}
