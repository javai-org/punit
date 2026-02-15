pluginManagement {
    includeBuild("punit-gradle-plugin")
}

rootProject.name = "punit"

// Include the outcome library from the local filesystem when available (sibling folder).
// On CI, this folder won't exist and Gradle resolves outcome from Maven Central instead.
val outcomeDir = file("../outcome")
if (outcomeDir.isDirectory) {
    includeBuild(outcomeDir) {
        dependencySubstitution {
            substitute(module("org.javai:outcome")).using(project(":"))
        }
    }
}
