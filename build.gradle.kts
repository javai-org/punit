plugins {
    id("java-library")
    id("maven-publish")
    id("jacoco")
    idea
}

// Configure IDEA to download sources and javadoc
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

group = "org.javai"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    
    // Generate sources and javadoc jars for publishing
    withSourcesJar()
    withJavadocJar()
}

// Compile with -parameters flag to preserve method parameter names at runtime
// This is required for use case argument injection
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// Define the experiment source set
sourceSets {
    val experiment by creating {
        java.srcDir("src/experiment/java")
        resources.srcDir("src/experiment/resources")
        
        // experiment depends on main's output
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    
    test {
        // test depends on both main and experiment
        compileClasspath += experiment.output
        runtimeClasspath += experiment.output
    }
}

// Configure dependencies for experiment source set
val experimentImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

val experimentRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

repositories {
    mavenCentral()
}

dependencies {
    // JUnit 5 Jupiter API - needed at compile time for the extension
    // Using 'api' so consumers get transitive access to JUnit types
    // Version 5.13.3 includes failureThreshold for @RepeatedTest
    api(platform("org.junit:junit-bom:5.13.3"))
    api("org.junit.jupiter:junit-jupiter-api")
    
    // Apache Commons Statistics - for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.2")

    // Log4j 2 - structured logging for runtime output
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.23.1")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.apache.logging.log4j:log4j-core:2.23.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Experiment dependencies - experiments use JUnit's TestTemplate mechanism
    experimentImplementation("org.junit.jupiter:junit-jupiter")
    experimentImplementation("org.assertj:assertj-core:3.27.6")
    experimentRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Ensure build order: main -> experiment -> test
tasks.named("compileExperimentJava") {
    dependsOn(tasks.compileJava)
}

tasks.named("compileTestJava") {
    dependsOn("compileExperimentJava")
}

tasks.named("processExperimentResources") {
    dependsOn(tasks.processResources)
}

tasks.named("processTestResources") {
    dependsOn("processExperimentResources")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Exclude test subject classes from direct discovery
    // They are executed via TestKit in integration tests
    exclude("**/testsubjects/**")
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

// Shared configuration for experiment tasks
fun Test.configureAsExperimentTask() {
    group = "verification"
    
    // Use the experiment source set
    testClassesDirs = sourceSets["experiment"].output.classesDirs
    classpath = sourceSets["experiment"].runtimeClasspath
    
    useJUnitPlatform()
    
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
    
    // Experiments never fail the build (they're exploratory, not conformance tests)
    ignoreFailures = true
    
    // Ensure experiment classes are compiled first
    dependsOn("compileExperimentJava", "processExperimentResources")
    
    // Support simplified syntax: ./gradlew exp -Prun=TestName
    // This avoids the verbose --tests "TestName" syntax
    val runFilter = project.findProperty("run") as String?
    if (runFilter != null) {
        filter {
            includeTestsMatching("*$runFilter*")
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
        
        val specsUpdated = specsFile.exists() && specsFile.walkTopDown()
            .filter { it.isFile && it.lastModified() >= startTime }
            .any()
        val explorationsUpdated = explorationsFile.exists() && explorationsFile.walkTopDown()
            .filter { it.isFile && it.lastModified() >= startTime }
            .any()
        
        if (specsUpdated) {
            println("  MEASURE specs written to: $specsDir/")
        }
        if (explorationsUpdated) {
            println("  EXPLORE results written to: $explorationsDir/")
        }
        if (!specsUpdated && !explorationsUpdated) {
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            pom {
                name.set("PUNIT")
                description.set("Probabilistic Unit Testing Framework for JUnit 5 - Test non-deterministic systems with statistical pass/fail thresholds")
                url.set("https://github.com/javai-org/punit")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("javai")
                        name.set("JAVAI")
                        organization.set("javai.org")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/javai-org/punit.git")
                    developerConnection.set("scm:git:ssh://github.com/javai-org/punit.git")
                    url.set("https://github.com/javai-org/punit")
                }
            }
        }
    }
    
    repositories {
        // Publish to local Maven repository (~/.m2/repository)
        mavenLocal()
        
        // Publish to project's build directory (for CI artifacts)
        maven {
            name = "buildDir"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

// Convenience task to build and publish locally
tasks.register("publishLocal") {
    group = "publishing"
    description = "Builds and publishes to local Maven repository"
    dependsOn("publishMavenJavaPublicationToMavenLocalRepository")
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
                exclude(
                    "**/examples/**",
                    "**/experiment/**"
                )
            }
        })
    )
}
