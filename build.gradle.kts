plugins {
    id("java-library")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("jacoco")
    idea
    kotlin("jvm") version "2.3.10"
}

// Configure IDEA to download sources and javadoc
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

signing {
    useGpgCmd()
}

group = "org.javai"
version = "0.2.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Compile with -parameters flag to preserve method parameter names at runtime
// This is required for use case argument injection
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    // JUnit 5 Jupiter API - needed at compile time for the extension
    // Using 'api' so consumers get transitive access to JUnit types
    // Version 5.13.3 includes failureThreshold for @RepeatedTest
    api(platform("org.junit:junit-bom:5.14.2"))
    api("org.junit.jupiter:junit-jupiter-api")
    
    // Apache Commons Statistics - for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.2")

    // SnakeYAML - for YAML serialization in spec generation
    implementation("org.yaml:snakeyaml:2.5")

    // Jackson - for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.0")

    // Outcome - result types for contract postconditions
    // Resolved locally via composite build (settings.gradle.kts), or from Maven Central on CI
    api("org.javai:outcome:0.1.0")

    // Optional JSON matching support for instance conformance
    // Users who want JsonMatcher need to add this dependency to their project
    compileOnly("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    // Bridge SLF4J to Log4j2 (some dependencies use SLF4J)
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    testImplementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ═══════════════════════════════════════════════════════════════════════════
// SCRIPTS source set — Kotlin utility scripts (e.g. verdict catalogue generator)
// ═══════════════════════════════════════════════════════════════════════════
val scriptsSourceSet: SourceSet = sourceSets.create("scripts")
scriptsSourceSet.kotlin.srcDir("src/scripts/kotlin")

dependencies {
    "scriptsImplementation"(kotlin("stdlib"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("punit-experiment")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Exclude test subject classes from direct discovery
    // They are executed via TestKit in integration tests
    exclude("**/testsubjects/**")

    // Forward punit.* system properties to the test JVM
    // Enables e.g. -Dpunit.stats.detailLevel=SUMMARY from the command line
    System.getProperties()
        .filter { (k, _) -> k.toString().startsWith("punit.") }
        .forEach { (k, v) -> systemProperty(k.toString(), v.toString()) }

    // Support simplified syntax: ./gradlew test -Prun=TestName.testMethod
    val runFilter = project.findProperty("run") as String?
    if (runFilter != null) {
        filter {
            if (runFilter.contains(".")) {
                includeTestsMatching("*$runFilter")
            } else {
                includeTestsMatching("*$runFilter*")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EXPERIMENT tasks - Run experiments (MEASURE or EXPLORE mode)
// ═══════════════════════════════════════════════════════════════════════════
//
// Runs experiments annotated with @Experiment. The mode (MEASURE or EXPLORE)
// is determined from the annotation's mode property.
//
// Usage (simplified with -Prun):
//   ./gradlew exp -Prun=ShoppingExperiment.measureRealisticSearchBaseline
//   ./gradlew experiment -Prun=ShoppingExperiment
//
// Traditional --tests syntax also works:
//   ./gradlew exp --tests "ShoppingExperiment.measureRealisticSearchBaseline"
//
// Output:
//   MEASURE mode: Specs written to src/test/resources/punit/specs/{UseCaseId}.yaml
//   EXPLORE mode: Specs written to src/test/resources/punit/explorations/{UseCaseId}/{config}.yaml
//

// Output directories for experiment modes
val specsDir = "src/test/resources/punit/specs"
val explorationsDir = "src/test/resources/punit/explorations"
val optimizationsDir = "src/test/resources/punit/optimizations"

// Shared configuration for experiment tasks
fun Test.configureAsExperimentTask() {
    group = "verification"

    // Use the test source set (experiments live alongside tests)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform {
        includeTags("punit-experiment")
    }

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }

    // Configure reports output directory
    reports {
        html.outputLocation.set(layout.buildDirectory.dir("reports/experiment"))
        junitXml.outputLocation.set(layout.buildDirectory.dir("experiment-results"))
    }

    // Output directories for each mode (used by the framework based on annotation mode)
    systemProperty("punit.specs.outputDir", specsDir)
    systemProperty("punit.explorations.outputDir", explorationsDir)
    systemProperty("punit.optimizations.outputDir", optimizationsDir)

    // Deactivate @Disabled so experiments can run even when disabled
    // (they're @Disabled to prevent accidental execution in regular test runs)
    systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")

    // Experiments never fail the build (they're exploratory, not conformance tests)
    ignoreFailures = true

    // Exclude test subject classes (they are executed via TestKit in integration tests)
    exclude("**/testsubjects/**")

    // Ensure test classes are compiled first
    dependsOn("compileTestJava", "processTestResources")
    
    // Support simplified syntax: ./gradlew exp -Prun=TestName
    // This avoids the verbose --tests "TestName" syntax
    val runFilter = project.findProperty("run") as String?
    if (runFilter != null) {
        filter {
            if (runFilter.contains(".")) {
                // Class.method specified - match precisely (no trailing wildcard)
                includeTestsMatching("*$runFilter")
            } else {
                // Just class name - match all methods in the class
                includeTestsMatching("*$runFilter*")
            }
        }
    }
    
    // Track start time to detect which directories received output
    var startTime = 0L
    doFirst {
        startTime = System.currentTimeMillis()
    }
    
    doLast {
        println("\n✓ Experiment complete.")

        // Check which directories received new files during this run
        val specsFile = file(specsDir)
        val explorationsFile = file(explorationsDir)
        val optimizationsFile = file(optimizationsDir)

        val specsUpdated = specsFile.exists() && specsFile.walkTopDown()
            .filter { it.isFile && it.lastModified() >= startTime }
            .any()
        val explorationsUpdated = explorationsFile.exists() && explorationsFile.walkTopDown()
            .filter { it.isFile && it.lastModified() >= startTime }
            .any()
        val optimizationsUpdated = optimizationsFile.exists() && optimizationsFile.walkTopDown()
            .filter { it.isFile && it.lastModified() >= startTime }
            .any()

        if (specsUpdated) {
            println("  MEASURE specs written to: $specsDir/")
        }
        if (explorationsUpdated) {
            println("  EXPLORE results written to: $explorationsDir/")
        }
        if (optimizationsUpdated) {
            println("  OPTIMIZE results written to: $optimizationsDir/")
        }
        if (!specsUpdated && !explorationsUpdated && !optimizationsUpdated) {
            println("  No output files were written.")
        }
    }
}

val experiment by tasks.registering(Test::class) {
    description = "Runs experiments (mode determined from @Experiment annotation)"
    configureAsExperimentTask()
}

// 'exp' is a full Test task (not just an alias) so --tests works
val exp by tasks.registering(Test::class) {
    description = "Shorthand for 'experiment' task"
    configureAsExperimentTask()
}

// ═══════════════════════════════════════════════════════════════════════════
// VERDICT CATALOGUE generation — run VerdictCatalogueTest at both detail
// levels, then assemble docs/VERDICT-CATALOG.md via a Kotlin script.
// ═══════════════════════════════════════════════════════════════════════════
//
// Usage:
//   ./gradlew generateVerdictCatalog
//

fun Test.configureAsVerdictCatalogueTask(detailLevel: String) {
    group = "documentation"
    description = "Runs VerdictCatalogueTest with punit.stats.detailLevel=$detailLevel"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    // Deactivate @Disabled so the catalogue test can run
    systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
    systemProperty("punit.stats.detailLevel", detailLevel)

    filter { includeTestsMatching("*VerdictCatalogueTest*") }

    // Some scenarios intentionally fail
    ignoreFailures = true

    exclude("**/testsubjects/**")

    dependsOn("compileTestJava", "processTestResources")
}

val verdictCatalogueSummary by tasks.registering(Test::class) {
    configureAsVerdictCatalogueTask("SUMMARY")
}

val verdictCatalogueVerbose by tasks.registering(Test::class) {
    configureAsVerdictCatalogueTask("VERBOSE")
}

val generateVerdictCatalog by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Generates docs/VERDICT-CATALOG.md from VerdictCatalogueTest output"

    classpath = sourceSets["scripts"].runtimeClasspath
    mainClass.set("org.javai.punit.scripts.GenerateVerdictCatalogKt")
    workingDir = projectDir

    dependsOn(verdictCatalogueSummary, verdictCatalogueVerbose)
}

tasks.javadoc {
    options {
        (this as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            charSet = "UTF-8"
            // Suppress warnings for missing javadoc
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUNIT - Probabilistic Unit Testing Framework",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit", version.toString())

    pom {
        name.set("PUnit")
        description.set("Probabilistic Unit Testing Framework for JUnit 5 - Test non-deterministic systems with statistical pass/fail thresholds")
        url.set("https://github.com/javai-org/punit")

        licenses {
            license {
                name.set("Attribution Required License (ARL-1.0)")
                url.set("https://github.com/javai-org/punit/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mikemannion")
                name.set("Michael Franz Mannion")
                email.set("michaelmannion@me.com")
            }
        }

        scm {
            url.set("https://github.com/javai-org/punit")
            connection.set("scm:git:git://github.com/javai-org/punit.git")
            developerConnection.set("scm:git:ssh://github.com/javai-org/punit.git")
        }
    }
}

// Convenience task to build and publish locally
tasks.register("publishLocal") {
    description = "Publishes to the local Maven repository"
    group = "publishing"
    dependsOn(tasks.publishToMavenLocal)
}


// ========== Code Coverage (JaCoCo) ==========

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
    
    // Exclude example classes from coverage analysis
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/examples/**")
            }
        })
    )
}

