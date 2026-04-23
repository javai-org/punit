plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit API",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.api"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-api", version.toString())

    pom {
        name.set("PUnit API")
        description.set("Author-facing typed value types for PUnit probabilistic testing (UseCase, FactorBundle, FactorValue, UseCaseOutcome)")
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
