plugins {
    id("fabric-loom") apply false
    id("net.fabricmc.fabric-loom") apply false
}
fun targetProperty(key: String): String = sc.properties.get<String>(key)
val minecraft = sc.current.version
val obfuscated = targetProperty("mod.obfuscated").toBoolean()
apply(plugin = if (obfuscated) "fabric-loom" else "net.fabricmc.fabric-loom")
group = targetProperty("mod.group")
version = providers.gradleProperty("mod_version").get()

// Runtime packages and Mixin names stay unchanged; only compilation selects API differences.
extra["anticrashTarget"] = mapOf(
    "minecraft" to minecraft,
    "obfuscated" to obfuscated,
    "java" to targetProperty("mod.java"),
    "compiler" to targetProperty("mod.compile_java"),
    "loader" to targetProperty("deps.fabric_loader"),
    "fabricApi" to targetProperty("deps.fabric_api"),
    "compileApi" to targetProperty("mod.compile_fabric_api"),
    "offlineAccess" to targetProperty("mod.offline_access"),
    "legacyMixinAp" to targetProperty("mod.legacy_mixin_ap"),
    "qualification" to targetProperty("mod.qualification"),
    "mixinProfile" to targetProperty("resources.mixin_profile"),
    "refmap" to targetProperty("resources.refmap"),
    "entityAudit" to targetProperty("audit.entities"),
    "blockEntityAudit" to targetProperty("audit.block_entities")
)
apply(from = rootProject.file("gradle/minecraft.gradle"))
