plugins { id("dev.kikugie.stonecutter") }
stonecutter active "1.18.2"

// Native aggregation is useful in CI/IDEs. The Python runner uses separate Gradle
// invocations instead when bounding retained Minecraft models is more important.
tasks.register("buildAll") {
    group = "build"
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}
tasks.register("testAll") {
    group = "verification"
    dependsOn(stonecutter.versions.map { ":${it.project}:test" })
    dependsOn(":supervisor:test")
}
tasks.register("build") { dependsOn(":${requireNotNull(stonecutter.current).project}:build") }
tasks.register("test") { dependsOn(":${requireNotNull(stonecutter.current).project}:test", ":supervisor:test") }
