plugins { java }
group = "com.atsukimc.anticrash"
version = providers.gradleProperty("mod_version").get()
repositories { mavenCentral() }
dependencies { testImplementation("org.junit.jupiter:junit-jupiter:5.10.2"); testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach { useJUnitPlatform(); maxHeapSize = "512m" }
fun Jar.supervisorContents() {
    from(sourceSets.main.get().output)
    from(rootProject.file("LICENSE")) { rename { "LICENSE_AntiCrash-Supervisor" } }
    manifest { attributes("Main-Class" to "com.atsukimc.anticrash.supervisor.SupervisorMain") }
    archiveBaseName.set("AntiCrash-Supervisor")
}
tasks.jar { supervisorContents() }
val targets = rootProject.file("stonecutter.properties.toml").readLines().mapNotNull {
    Regex("""^\["([0-9.]+)"\]$""").matchEntire(it)?.groupValues?.get(1)
}
for (minecraft in targets) {
    tasks.register<Jar>("jarFor" + minecraft.replace('.', '_')) {
        dependsOn(tasks.classes)
        supervisorContents()
        archiveVersion.set("${project.version}+mc$minecraft")
    }
}
