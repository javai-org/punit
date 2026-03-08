package org.javai.punit.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File
import java.net.URLClassLoader

/**
 * Gradle plugin that configures PUnit probabilistic testing tasks.
 *
 * Applies to any project using PUnit:
 * - Configures the `test` task to exclude experiment-tagged tests
 * - Registers `experiment` and `exp` tasks for running experiments
 * - Registers `createSentinel` task to build an executable sentinel JAR
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

            registerCreateSentinelTask(project, extension)
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

            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")

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

            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
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

    private fun registerCreateSentinelTask(
        project: Project,
        extension: PunitExperimentExtension
    ) {
        project.tasks.register("createSentinel", Jar::class.java).configure {
            description = "Builds an executable sentinel JAR with all @Sentinel classes"
            group = "build"

            val testSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("test")
            val mainSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main")

            archiveClassifier.set("sentinel")

            manifest {
                attributes(mapOf(
                    "Main-Class" to "org.javai.punit.sentinel.SentinelMain",
                    "Implementation-Title" to "${project.name}-sentinel",
                    "Implementation-Version" to project.version
                ))
            }

            // Include compiled test and main classes
            from(testSourceSet.output)
            from(mainSourceSet.output)

            // Include all runtime dependencies, unpacking JARs
            from(testSourceSet.runtimeClasspath
                .filter { it.exists() }
                .map { if (it.isDirectory) it else project.zipTree(it) }
            )

            // Merge service files; first-wins for everything else
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            // Exclude testsubjects from the sentinel JAR if configured
            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            // Exclude test framework classes that are not needed at runtime
            exclude("**/archunit/**")
            exclude("META-INF/maven/**")
            exclude("META-INF/LICENSE*")
            exclude("META-INF/NOTICE*")

            dependsOn("compileTestJava", "processTestResources")

            // Scan for @Sentinel classes and generate the manifest at build time
            doFirst {
                val sentinelClasses = scanForSentinelClasses(testSourceSet.output.classesDirs.files,
                    testSourceSet.runtimeClasspath.files)

                if (sentinelClasses.isEmpty()) {
                    throw org.gradle.api.GradleException(
                        "No @Sentinel-annotated classes found in test sources. " +
                        "Annotate at least one reliability spec class with @Sentinel.")
                }

                // Write manifest to a temp directory that gets included in the JAR
                val manifestDir = project.layout.buildDirectory
                    .dir("generated/sentinel-manifest").get().asFile
                val manifestFile = File(manifestDir, "META-INF/punit/sentinel-classes")
                manifestFile.parentFile.mkdirs()
                manifestFile.writeText(sentinelClasses.joinToString("\n") + "\n")

                from(manifestDir)

                project.logger.lifecycle("Sentinel classes discovered:")
                sentinelClasses.forEach { project.logger.lifecycle("  $it") }
            }

            doLast {
                project.logger.lifecycle(
                    "Sentinel JAR created: ${archiveFile.get().asFile.relativeTo(project.projectDir)}")
            }
        }
    }

    /**
     * Scans compiled test class directories for classes annotated with @Sentinel.
     * Uses an isolated URLClassLoader to avoid polluting the plugin's classloader.
     */
    private fun scanForSentinelClasses(
        classesDirs: Set<File>,
        classpathFiles: Set<File>
    ): List<String> {
        val urls = (classesDirs + classpathFiles)
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        val sentinelAnnotation = try {
            classLoader.loadClass("org.javai.punit.api.Sentinel")
        } catch (e: ClassNotFoundException) {
            throw org.gradle.api.GradleException(
                "Could not find @Sentinel annotation on classpath. " +
                "Ensure punit-core or punit-sentinel is a dependency.")
        }

        val sentinelClasses = mutableListOf<String>()

        for (classesDir in classesDirs) {
            if (!classesDir.isDirectory) continue

            classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .filter { !it.path.contains("testsubjects") }
                .forEach { classFile ->
                    val relativePath = classFile.relativeTo(classesDir).path
                    val className = relativePath
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')

                    try {
                        val clazz = classLoader.loadClass(className)
                        if (clazz.annotations.any { sentinelAnnotation.isInstance(it) }) {
                            sentinelClasses.add(className)
                        }
                    } catch (e: Throwable) {
                        // Skip classes that can't be loaded (inner classes, etc.)
                    }
                }
        }

        classLoader.close()
        return sentinelClasses.sorted()
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
