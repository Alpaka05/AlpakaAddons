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

tasks.named<JavaExec>("runClient") {
    setDependsOn(listOf(tasks.jar.get()))
    mainClass.set("net.fabricmc.loader.impl.launch.knot.KnotClient")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = file("run")

    args(
        "--gameDir", file("run").absolutePath,
        "--assetsDir", file("${System.getProperty("user.home")}/.gradle/caches/fabric-loom/assets").absolutePath,
        "--assetIndex", "26.1.2"
    )

    jvmArgs(
        "-Dfabric.development=true",
        "-Dfabric.gameJarPath=" + file("${System.getProperty("user.home")}/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.1.2/minecraft-merged-deobf-26.1.2.jar").absolutePath
    )

    doFirst {
        file("run").mkdirs()
    }
}
