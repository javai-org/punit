plugins {
    id("java-library")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("jacoco")
    id("org.javai.punit")
    idea
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
version = property("punitVersion") as String

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

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
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


// ========== Release Lifecycle ==========

fun runCommand(vararg args: String) {
    val process = ProcessBuilder(*args)
        .directory(projectDir)
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Command failed (exit $exitCode): ${args.joinToString(" ")}")
    }
}

fun runCommandAndCapture(vararg args: String): String {
    val process = ProcessBuilder(*args)
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.trim()
}

tasks.register("release") {
    description = "Validates, publishes to Maven Central, tags the release, and bumps to next SNAPSHOT"
    group = "publishing"

    doLast {
        val ver = project.property("punitVersion") as String

        // 1. Validate not a SNAPSHOT
        if (ver.endsWith("-SNAPSHOT")) {
            throw GradleException(
                "Cannot release a SNAPSHOT version ($ver). " +
                "Set the release version in gradle.properties first, e.g. punitVersion=0.2.0"
            )
        }

        // 2. Validate clean git state
        val statusOutput = runCommandAndCapture("git", "status", "--porcelain")
        if (statusOutput.isNotEmpty()) {
            throw GradleException(
                "Cannot release with uncommitted changes. Commit or stash them first.\n$statusOutput"
            )
        }

        // 3. Create annotated tag locally (before publish, so a successful publish always has a tag)
        val tag = "v$ver"
        logger.lifecycle("Creating tag $tag...")
        runCommand("git", "tag", "-a", tag, "-m", "Release $ver")

        // 4. Publish to Maven Central (delete local tag if this fails)
        logger.lifecycle("Publishing $ver to Maven Central...")
        try {
            runCommand("./gradlew", "publishAndReleaseToMavenCentral")
        } catch (e: Exception) {
            logger.lifecycle("Publishing failed — removing local tag $tag")
            runCommand("git", "tag", "-d", tag)
            throw e
        }

        // 5. Push tag (artifact is published, so the tag must reach the remote)
        logger.lifecycle("Pushing tag $tag to origin...")
        runCommand("git", "push", "origin", tag)

        // 6. Bump to next SNAPSHOT
        val parts = ver.split(".")
        val nextPatch = parts[2].toInt() + 1
        val nextVersion = "${parts[0]}.${parts[1]}.$nextPatch-SNAPSHOT"
        logger.lifecycle("Bumping version to $nextVersion...")

        val rootProps = file("gradle.properties")
        rootProps.writeText(rootProps.readText().replace("punitVersion=$ver", "punitVersion=$nextVersion"))

        val pluginProps = file("punit-gradle-plugin/gradle.properties")
        pluginProps.writeText(pluginProps.readText().replace("punitVersion=$ver", "punitVersion=$nextVersion"))

        runCommand("git", "add", "gradle.properties", "punit-gradle-plugin/gradle.properties")
        runCommand("git", "commit", "-m", "Bump version to $nextVersion")
        runCommand("git", "push")

        logger.lifecycle("Release $ver complete. Version bumped to $nextVersion.")
    }
}

tasks.register("tagRelease") {
    description = "Creates and pushes a release tag for a given version (e.g. -PreleaseVersion=0.1.0)"
    group = "publishing"

    doLast {
        val ver = project.findProperty("releaseVersion") as String?
            ?: throw GradleException("Specify -PreleaseVersion=<version>, e.g. ./gradlew tagRelease -PreleaseVersion=0.1.0")

        val tag = "v$ver"
        val commitish = (project.findProperty("commitish") as String?) ?: "HEAD"

        logger.lifecycle("Creating tag $tag at $commitish...")
        runCommand("git", "tag", "-a", tag, commitish, "-m", "Release $ver")

        logger.lifecycle("Pushing tag $tag to origin...")
        runCommand("git", "push", "origin", tag)

        logger.lifecycle("Tag $tag created and pushed.")
    }
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
}
