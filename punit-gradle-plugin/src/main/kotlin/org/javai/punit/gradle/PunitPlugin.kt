package org.javai.punit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
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
            // Measure specs are inputs to subsequent @ProbabilisticTest runs
            // (including in CI), so they live alongside source and are intended
            // to be committed. Explore and Optimize outputs are human-review
            // artefacts with no downstream programmatic consumer, so they live
            // under build/ as transient build output. See CHANGELOG 0.6.0.
            specsDir.convention("src/test/resources/punit/specs")
            explorationsDir.convention(
                project.layout.buildDirectory.dir("punit/explorations").map { it.asFile.absolutePath }
            )
            optimizationsDir.convention(
                project.layout.buildDirectory.dir("punit/optimizations").map { it.asFile.absolutePath }
            )
            configureTestTask.convention(true)
            excludeTestSubjects.convention(true)
        }

        // Create a dedicated configuration for punit-sentinel so it is only
        // resolved when the createSentinel task actually runs, not on every build
        val sentinelConfig = project.configurations.create("punitSentinel") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        val punitVersion = loadPunitVersion()
        project.dependencies.add("punitSentinel",
            "org.javai:punit-sentinel:$punitVersion")

        // Create a dedicated configuration for punit-report (HTML report generation)
        val reportConfig = project.configurations.create("punitReport") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        project.dependencies.add("punitReport",
            "org.javai:punit-report:$punitVersion")

        project.afterEvaluate {
            if (extension.configureTestTask.get()) {
                configureTestTask(project, extension)
            }

            registerExperimentTask(project, extension, "experiment",
                "Runs experiments (mode determined from @Experiment annotation)")
            registerExperimentTask(project, extension, "exp",
                "Shorthand for 'experiment' task")

            registerCreateSentinelTask(project, extension, sentinelConfig)
            registerPunitReportTask(project, reportConfig)
            registerPunitVerifyTask(project, reportConfig)
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

            // Set default report directory so VerdictXmlSink writes to where punitReport reads
            if (System.getProperty("punit.report.dir") == null) {
                systemProperty("punit.report.dir",
                    project.layout.buildDirectory.dir("reports/punit/xml").get().asFile.absolutePath)
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
        extension: PunitExperimentExtension,
        sentinelConfig: org.gradle.api.artifacts.Configuration
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
            // Ensure all upstream project JARs (including transitive) are built
            // before we resolve the classpath. Without this, transitive project
            // dependencies (e.g., app -> app-usecases -> app-tests) may be
            // missing from the fat JAR.
            dependsOn(testSourceSet.runtimeClasspath.buildDependencies)
            dependsOn(sentinelConfig.buildDependencies)

            // Because all from() calls are deferred to doFirst (to avoid
            // configuration-time classpath resolution in composite builds),
            // Gradle has no visibility of the actual inputs and cannot
            // determine staleness. Force re-execution every time.
            outputs.upToDateWhen { false }

            // All from() calls are deferred to doFirst to avoid resolving
            // testRuntimeClasspath at configuration time, which breaks
            // composite builds where included projects are not yet evaluated.
            doFirst {
                // Include compiled test and main classes
                from(testSourceSet.output)
                from(mainSourceSet.output)

                // Include all runtime dependencies, unpacking JARs
                from(testSourceSet.runtimeClasspath
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else project.zipTree(it) }
                )

                // Include punit-sentinel runtime (SentinelMain, SentinelRunner)
                from(sentinelConfig.resolve()
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else project.zipTree(it) }
                )

                val sentinelClasses = scanForSentinelClasses(testSourceSet.output.classesDirs.files,
                    testSourceSet.runtimeClasspath.files + sentinelConfig.resolve())

                if (sentinelClasses.isEmpty()) {
                    throw org.gradle.api.GradleException(
                        "No @Sentinel-annotated classes found on the test classpath. " +
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
     * Scans compiled class directories and JARs on the classpath for classes
     * annotated with @Sentinel. Uses an isolated URLClassLoader to avoid
     * polluting the plugin's classloader.
     *
     * @param classesDirs  test source output directories (always scanned)
     * @param classpathFiles  full runtime classpath — directories are scanned
     *                        for @Sentinel classes; JARs provide the classloader
     *                        context but are not scanned (sentinel classes must
     *                        come from project sources, not third-party libraries)
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

        // Scan test output dirs and all class directories on the classpath
        val allClassesDirs = (classesDirs + classpathFiles)
            .filter { it.isDirectory && it.exists() }
            .distinct()

        for (classesDir in allClassesDirs) {
            scanDirectory(classesDir, classLoader, sentinelAnnotation, sentinelClasses)
        }

        // Also scan JARs on the classpath — project dependencies are packaged
        // as JARs by Gradle, so @Sentinel classes from sibling modules appear here
        for (jar in classpathFiles) {
            if (!jar.isFile || !jar.name.endsWith(".jar")) continue
            scanJar(jar, classLoader, sentinelAnnotation, sentinelClasses)
        }

        classLoader.close()
        return sentinelClasses.sorted()
    }

    private fun scanDirectory(
        classesDir: File,
        classLoader: URLClassLoader,
        sentinelAnnotation: Class<*>,
        results: MutableList<String>
    ) {
        classesDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .filter { !it.path.contains("testsubjects") }
            .forEach { classFile ->
                val className = classFile.relativeTo(classesDir).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
                checkForSentinel(className, classLoader, sentinelAnnotation, results)
            }
    }

    private fun scanJar(
        jar: File,
        classLoader: URLClassLoader,
        sentinelAnnotation: Class<*>,
        results: MutableList<String>
    ) {
        try {
            java.util.jar.JarFile(jar).use { jarFile ->
                jarFile.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .filter { !it.name.contains("testsubjects") }
                    .forEach { entry ->
                        val className = entry.name
                            .removeSuffix(".class")
                            .replace('/', '.')
                        checkForSentinel(className, classLoader, sentinelAnnotation, results)
                    }
            }
        } catch (_: Exception) {
            // Skip JARs that can't be read
        }
    }

    private fun checkForSentinel(
        className: String,
        classLoader: URLClassLoader,
        sentinelAnnotation: Class<*>,
        results: MutableList<String>
    ) {
        try {
            val clazz = classLoader.loadClass(className)
            if (clazz.annotations.any { sentinelAnnotation.isInstance(it) }) {
                results.add(className)
            }
        } catch (_: Throwable) {
            // Skip classes that can't be loaded
        }
    }

    private fun registerPunitReportTask(
        project: Project,
        reportConfig: org.gradle.api.artifacts.Configuration
    ) {
        project.tasks.register("punitReport", PunitReportTask::class.java).configure {
            description = "Generates an HTML report from PUnit test verdict XML files"
            group = "verification"
            xmlDir.set(project.layout.buildDirectory.dir("reports/punit/xml"))
            htmlDir.set(project.layout.buildDirectory.dir("reports/punit/html"))
            reportClasspath.from(reportConfig)
        }
    }

    private fun registerPunitVerifyTask(
        project: Project,
        reportConfig: org.gradle.api.artifacts.Configuration
    ) {
        val verifyTask = project.tasks.register("punitVerify", PunitVerifyTask::class.java) {
            description = "Verifies that all probabilistic test verdicts passed"
            group = "verification"
            xmlDir.set(project.layout.buildDirectory.dir("reports/punit/xml"))
            reportClasspath.from(reportConfig)

            // Run after tests so verdict XMLs are available
            mustRunAfter(project.tasks.named("test"))
        }

        // Wire into the check lifecycle
        project.tasks.named("check").configure {
            dependsOn(verifyTask)
        }
    }

    private fun loadPunitVersion(): String = PunitVersion.VERSION

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
