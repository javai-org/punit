plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

dependencies {
    // JUnit Jupiter API — compileOnly per DD-05: annotations reference JUnit meta-annotations
    // but punit-core does not transitively require JUnit at runtime
    compileOnly("org.junit.jupiter:junit-jupiter-api")

    // Apache Commons Statistics — for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.2")

    // SnakeYAML — for YAML serialization in spec generation
    implementation("org.yaml:snakeyaml:2.5")

    // Jackson — for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.0")

    // Outcome — result types for contract postconditions
    api("org.javai:outcome:0.1.0")

    // Optional JSON matching support for instance conformance
    compileOnly("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    testImplementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
}

// --- javai-R conformance reference data ----------------------------------------

val javaiRVersion: String by project.rootProject.extra {
    project.rootProject.property("javaiR.version") as String
}

val conformanceDir = layout.buildDirectory.dir("conformance")

val downloadConformanceData by tasks.registering {
    description = "Downloads javai-R conformance reference data (v$javaiRVersion)"
    group = "verification"

    val zipFile = layout.buildDirectory.file("conformance/cases-v$javaiRVersion.zip")
    outputs.dir(conformanceDir)

    doLast {
        val dir = conformanceDir.get().asFile
        dir.mkdirs()
        val zip = zipFile.get().asFile
        if (!zip.exists()) {
            val url = "https://github.com/javai-org/javai-R/releases/download/v$javaiRVersion/cases-v$javaiRVersion.zip"
            uri(url).toURL().openStream().use { input ->
                zip.outputStream().use { output -> input.copyTo(output) }
            }
        }
        copy {
            from(zipTree(zip))
            into(dir)
        }
    }
}

// ---------------------------------------------------------------------------------

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Core",
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
        description.set("Core statistical engine, annotations, and contracts for PUnit probabilistic testing")
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
