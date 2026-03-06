plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("org.javai.punit")
}

signing {
    useGpgCmd()
}

dependencies {
    api(project(":punit-core"))
    api("org.junit.jupiter:junit-jupiter-api")

    // Jackson — needed by InputSourceResolver for JSON/CSV parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.0")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

    // Test
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    testImplementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit JUnit 5",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.junit5"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-junit5", version.toString())

    pom {
        name.set("PUnit JUnit 5")
        description.set("JUnit 5 extensions for PUnit probabilistic testing")
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
