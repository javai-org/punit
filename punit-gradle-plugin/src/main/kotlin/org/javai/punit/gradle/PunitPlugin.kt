package org.javai.punit.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

/**
 * Gradle plugin that configures PUnit probabilistic testing tasks.
 *
 * Applies to any project using PUnit:
 * - Configures the `test` task to exclude experiment-tagged tests
 * - Registers `experiment` and `exp` tasks for running experiments
 * - Forwards `punit.*` system properties and supports `-Prun=` filter syntax
 */
class PunitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("punit", PunitExperimentExtension::class.java).apply {
            specsDir.convention("src/test/resources/punit/specs")
            explorationsDir.convention("src/test/resources/punit/explorations")
            optimizationsDir.convention("src/test/resources/punit/optimizations")
            configureTestTask.convention(true)
            excludeTestSubjects.convention(true)
        }

        project.afterEvaluate {
            if (extension.configureTestTask.get()) {
                configureTestTask(project, extension)
            }

            registerExperimentTask(project, extension, "experiment",
                "Runs experiments (mode determined from @Experiment annotation)")
            registerExperimentTask(project, extension, "exp",
                "Shorthand for 'experiment' task")
        }
    }

    private fun configureTestTask(project: Project, extension: PunitExperimentExtension) {
        project.tasks.withType(Test::class.java).named("test").configure {
            useJUnitPlatform {
                excludeTags("punit-experiment")
            }

            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            forwardPunitSystemProperties(this)
            applyRunFilter(project, this)
        }
    }

    private fun registerExperimentTask(
        project: Project,
        extension: PunitExperimentExtension,
        taskName: String,
        taskDescription: String
    ) {
        project.tasks.register(taskName, Test::class.java).configure {
            description = taskDescription
            group = "verification"

            val testSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("test")

            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath

            useJUnitPlatform {
                includeTags("punit-experiment")
            }

            testLogging {
                events = setOf(
                    TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR
                )
                showExceptions = true
                showCauses = true
                showStackTraces = true
                exceptionFormat = TestExceptionFormat.FULL
                showStandardStreams = true
            }

            reports {
                html.outputLocation.set(project.layout.buildDirectory.dir("reports/experiment"))
                junitXml.outputLocation.set(project.layout.buildDirectory.dir("experiment-results"))
            }

            val specsDir = extension.specsDir.get()
            val explorationsDir = extension.explorationsDir.get()
            val optimizationsDir = extension.optimizationsDir.get()

            systemProperty("punit.specs.outputDir", specsDir)
            systemProperty("punit.explorations.outputDir", explorationsDir)
            systemProperty("punit.optimizations.outputDir", optimizationsDir)

            // Deactivate @Disabled so experiments can run
            systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")

            ignoreFailures = true

            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            dependsOn("compileTestJava", "processTestResources")

            forwardPunitSystemProperties(this)
            applyRunFilter(project, this)

            // Track start time to detect which directories received output
            var startTime = 0L
            doFirst {
                startTime = System.currentTimeMillis()
            }

            doLast {
                println("\n✓ Experiment complete.")

                val specsFile = project.file(specsDir)
                val explorationsFile = project.file(explorationsDir)
                val optimizationsFile = project.file(optimizationsDir)

                val specsUpdated = specsFile.exists() && specsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()
                val explorationsUpdated = explorationsFile.exists() && explorationsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()
                val optimizationsUpdated = optimizationsFile.exists() && optimizationsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()

                if (specsUpdated) {
                    println("  MEASURE specs written to: $specsDir/")
                }
                if (explorationsUpdated) {
                    println("  EXPLORE results written to: $explorationsDir/")
                }
                if (optimizationsUpdated) {
                    println("  OPTIMIZE results written to: $optimizationsDir/")
                }
                if (!specsUpdated && !explorationsUpdated && !optimizationsUpdated) {
                    println("  No output files were written.")
                }
            }
        }
    }

    private fun forwardPunitSystemProperties(task: Test) {
        System.getProperties()
            .filter { (k, _) -> k.toString().startsWith("punit.") }
            .forEach { (k, v) -> task.systemProperty(k.toString(), v.toString()) }
    }

    private fun applyRunFilter(project: Project, task: Test) {
        val runFilter = project.findProperty("run") as String?
        if (runFilter != null) {
            if (runFilter.contains(".")) {
                task.filter.includeTestsMatching("*$runFilter")
            } else {
                task.filter.includeTestsMatching("*$runFilter*")
            }
        }
    }
}
