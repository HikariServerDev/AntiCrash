plugins { id("dev.kikugie.stonecutter") }
stonecutter active "26.3"

// Preserve Stonecutter's inactive code and directives through the existing formatter.
val python = providers.environmentVariable("PYTHON").getOrElse("python3")
tasks.register<Exec>("formatCheck") {
    group = "verification"
    description = "Check Java formatting without changing version branches."
    commandLine(python, "scripts/format_java.py", "--check")
}
tasks.register<Exec>("formatApply") {
    group = "formatting"
    description = "Format Java while preserving inactive Stonecutter branches."
    commandLine(python, "scripts/format_java.py")
}
tasks.register<Exec>("sourceCheck") {
    group = "verification"
    commandLine(python, "scripts/check_sources.py")
}
tasks.register<Exec>("scriptTest") {
    group = "verification"
    commandLine(python, "-m", "unittest", "discover", "-s", "scripts", "-v")
}
val qualityCheck = tasks.register("qualityCheck") {
    group = "verification"
    description = "Check formatting, source ownership and build-tool regression tests."
    dependsOn("formatCheck", "sourceCheck", "scriptTest")
}

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
tasks.register("build") { dependsOn(qualityCheck, ":${requireNotNull(stonecutter.current).project}:build") }
tasks.register("test") { dependsOn(":${requireNotNull(stonecutter.current).project}:test", ":supervisor:test") }
tasks.register("check") { dependsOn(qualityCheck, "test") }
