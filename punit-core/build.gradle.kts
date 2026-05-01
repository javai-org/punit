import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

dependencies {
    // JUnit Jupiter API — compileOnly because annotations reference JUnit
    // meta-annotations but punit-core does not transitively require JUnit
    // at runtime.
    compileOnly("org.junit.jupiter:junit-jupiter-api")

    // opentest4j — the de-facto contract for non-JUnit test-failure
    // signalling. PUnit.assertPasses() throws AssertionFailedError /
    // TestAbortedException to translate FAIL / INCONCLUSIVE verdicts;
    // opentest4j has no JUnit Platform engine dependency, so a sentinel-
    // deployed class can drive PUnit without dragging in JUnit Jupiter
    // or Platform.
    api("org.opentest4j:opentest4j:1.3.0")

    // Apache Commons Statistics — for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.2")

    // SnakeYAML — for YAML serialization in spec generation
    implementation("org.yaml:snakeyaml:2.6")

    // Jackson — for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.2")

    // Outcome — result types for contract postconditions
    api("org.javai:outcome:0.2.0")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.25.4")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.4")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
}

// --- javai-R conformance reference data ----------------------------------------
// Fetches the latest javai-R release (resolved via the GitHub /releases/latest
// redirect, which costs no API-rate-limit quota), downloads its cases-<tag>.zip
// asset, caches it keyed by tag, and extracts into a directory on the test
// classpath so that /conformance/*.json resolves.

val conformanceResourcesDir = layout.buildDirectory.dir("generated/conformance")

val fetchConformanceData by tasks.registering {
    description = "Fetches the latest javai-R conformance reference data release"
    group = "verification"

    outputs.dir(conformanceResourcesDir)
    outputs.upToDateWhen { false }

    doLast {
        val latestUrl = URI(
            "https://github.com/javai-org/javai-R/releases/latest"
        ).toURL()
        val conn = latestUrl.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "HEAD"
        val status = conn.responseCode
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        require(status in 301..308 && location != null) {
            "Expected redirect from $latestUrl, got $status (location=$location)"
        }
        val tag = location.substringAfterLast("/tag/")
        require(tag.matches(Regex("^v\\d+\\.\\d+\\.\\d+$"))) {
            "Unexpected tag format '$tag' in $location"
        }

        val cacheZip = layout.buildDirectory
            .file("conformance-cache/cases-$tag.zip").get().asFile
        if (!cacheZip.exists()) {
            cacheZip.parentFile.mkdirs()
            val assetUrl = URI(
                "https://github.com/javai-org/javai-R/releases/download/$tag/cases-$tag.zip"
            ).toURL()
            assetUrl.openStream().use { input ->
                cacheZip.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val destDir = conformanceResourcesDir.get().asFile.resolve("conformance")
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        copy {
            from(zipTree(cacheZip))
            into(destDir)
        }
        logger.lifecycle("Fetched javai-R conformance fixtures: $tag")
    }
}

sourceSets.test {
    resources.srcDir(conformanceResourcesDir)
}

tasks.named<ProcessResources>("processTestResources") {
    dependsOn(fetchConformanceData)
}

// ---------------------------------------------------------------------------------

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.core"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-core", version.toString())

    pom {
        name.set("PUnit Core")
        description.set("PUnit probabilistic testing — author-facing API (UseCase, Contract, Sampling, criteria), execution engine, statistics, baselines, runtime entry point. JUnit-free; sentinel-deployable directly.")
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
