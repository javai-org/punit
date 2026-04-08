plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

dependencies {
    api(project(":punit-core"))
    testImplementation(project(":punit-junit5"))
}

tasks.test {
    // Test subjects are executed via SentinelRunner, not directly by JUnit
    exclude("**/testsubjects/**")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Sentinel",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.sentinel"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-sentinel", version.toString())

    pom {
        name.set("PUnit Sentinel")
        description.set("Sentinel mode for PUnit — always-on production monitoring")
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
