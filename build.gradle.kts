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
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")

    // Fabric Loader & API
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Mixin compile dependency
    compileOnly("org.spongepowered:mixin:0.8.5")
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
