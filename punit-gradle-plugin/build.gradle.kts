plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "org.javai"
version = property("punitVersion") as String

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("punit") {
            id = "org.javai.punit"
            implementationClass = "org.javai.punit.gradle.PunitPlugin"
            displayName = "PUnit Gradle Plugin"
            description = "Configures test and experiment tasks for PUnit probabilistic testing"
        }
    }
}

// Functional test source set
val functionalTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val functionalTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val functionalTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    functionalTestImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    functionalTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Generate a Kotlin source file with the PUnit version baked in at compile time.
// This avoids classloader/resource-loading issues with Gradle's pluginManagement { includeBuild }.
val punitVersion = property("punitVersion") as String
val generateVersionFile = tasks.register("generateVersionFile") {
    val outputDir = layout.buildDirectory.dir("generated/punit-version")
    outputs.dir(outputDir)
    inputs.property("punitVersion", punitVersion)
    doLast {
        val dir = outputDir.get().asFile.resolve("org/javai/punit/gradle")
        dir.mkdirs()
        dir.resolve("PunitVersion.kt").writeText(
            """
            package org.javai.punit.gradle

            internal object PunitVersion {
                const val VERSION = "$punitVersion"
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets.main.get().kotlin.srcDir(generateVersionFile.map { layout.buildDirectory.dir("generated/punit-version").get() })
tasks.named("compileKotlin") { dependsOn(generateVersionFile) }

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the functional tests for the plugin"
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()

    // Pass the punit root directory so functional tests can set up composite builds
    systemProperty("punitRootDir", rootProject.projectDir.parentFile.absolutePath)
}

tasks.check {
    dependsOn(functionalTestTask)
}
