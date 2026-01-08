plugins {
    id("java-library")
    id("maven-publish")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    
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
    implementation("org.apache.commons:commons-statistics-distribution:1.1")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Experiment dependencies - experiments use JUnit's TestTemplate mechanism
    experimentImplementation("org.junit.jupiter:junit-jupiter")
    experimentImplementation("org.assertj:assertj-core:3.24.2")
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

// Create experimentTests task for running experiments
// This is a standard JUnit Test task configured for the experiment source set.
// Experiments use JUnit's @TestTemplate mechanism under the hood.
//
// Usage:
//   ./gradlew experimentTests --tests "ShoppingExperiment"
//   ./gradlew experimentTests --tests "ShoppingExperiment.measureBasicSearchReliability"
//
val experimentTests by tasks.registering(Test::class) {
    description = "Runs experiment tests from src/experiment/java to generate empirical baselines"
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
        html.outputLocation.set(layout.buildDirectory.dir("reports/experimentTests"))
        junitXml.outputLocation.set(layout.buildDirectory.dir("experiment-results"))
    }
    
    // System properties for experiment configuration
    systemProperty("punit.mode", "experiment")
    systemProperty("punit.baseline.outputDir", layout.projectDirectory.dir("src/test/resources/punit/baselines").asFile.absolutePath)
    
    // Experiments never fail the build (they're exploratory, not conformance tests)
    ignoreFailures = true
    
    // Ensure experiment classes are compiled first
    dependsOn("compileExperimentJava", "processExperimentResources")
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
