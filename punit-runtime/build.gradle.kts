plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

dependencies {
    api(project(":punit-api"))
    api(project(":punit-core"))
    api(project(":punit-report"))

    // opentest4j carries the test-failure exception types (AssertionFailedError,
    // TestAbortedException) that PUnit.assertPasses() throws to translate
    // FAIL / INCONCLUSIVE verdicts. opentest4j has no JUnit Platform engine
    // dependency — it is the de-facto contract for non-JUnit test-failure
    // signalling, used here so a sentinel-deployed class can drive the
    // typed pipeline without dragging in JUnit Jupiter or Platform.
    api("org.opentest4j:opentest4j:1.3.0")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Runtime",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.runtime"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-runtime", version.toString())

    pom {
        name.set("PUnit Runtime")
        description.set("Runtime that drives a typed @ProbabilisticTest method to verdict — JUnit-free, consumed by both punit-junit5 and punit-sentinel")
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
