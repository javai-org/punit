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
    implementation("org.yaml:snakeyaml:2.6")

    // Jackson — for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.1")

    // Outcome — result types for contract postconditions
    api("org.javai:outcome:0.2.0")

    // Optional JSON matching support for instance conformance
    compileOnly("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    testImplementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
}

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
