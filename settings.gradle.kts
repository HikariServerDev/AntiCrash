pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
    plugins {
        id("fabric-loom") version "1.17.21"
        id("net.fabricmc.fabric-loom") version "1.17.21"
    }
}
plugins { id("dev.kikugie.stonecutter") version "0.9.8" }

val targets = file("stonecutter.properties.toml").readLines().mapNotNull {
    Regex("""^\["([0-9.]+)"\]$""").matchEntire(it)?.groupValues?.get(1)
}
require(targets.size == targets.distinct().size && targets.isNotEmpty())
val requested = providers.gradleProperty("mcVersion").orNull
require(requested == null || requested in targets) { "Unsupported Minecraft version: $requested" }
val active = Regex("stonecutter active \"([0-9.]+)\"")
    .find(file("stonecutter.gradle.kts").readText())!!.groupValues[1]
val nodes = if (requested == null) targets else listOf("1.18.2", active, requested).distinct()
stonecutter {
    create(rootProject) {
        versions(*nodes.toTypedArray())
        vcsVersion = "1.18.2"
    }
}
rootProject.name = "AntiCrash"
include(":supervisor")
