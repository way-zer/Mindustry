import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized

val appName = "Mindustry"
val Project.versionModifier: String get() = properties.getOrDefault("versionModifier", "release").toString()
val Project.buildVersion: String get() = properties.getOrDefault("buildversion", "custom build").toString()

fun Project.arcModule(name: String): String {
    val arcHash = property("archash")
    val localArc = !hasProperty("release") && !hasProperty("noLocalArc") && rootDir.resolveSibling("Arc").exists()
    return "com.github.Anuken${if (localArc) "" else ".Arc"}:$name:$arcHash"
}

fun Project.getModifierString(): String {
    if (versionModifier != "release") return "[${versionModifier.uppercase()}]"
    return ""
}

fun Project.generateDeployName(platform: String): String {
    @Suppress("NAME_SHADOWING")
    var platform = platform
    if (platform == "windows") {
        platform += "64"
    }
    platform = platform.capitalized()
    if (platform.endsWith("64") || platform.endsWith("32")) {
        platform = "${platform.substring(0, platform.length - 2)}-${platform.substring(platform.length - 2)}bit"
    }
    return "[${platform}]${getModifierString()}[v$buildVersion]${appName}"
}