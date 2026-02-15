package org.javai.punit.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@DisplayName("PUnit Gradle Plugin")
class PunitPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile get() = File(projectDir, "build.gradle.kts")
    private val settingsFile get() = File(projectDir, "settings.gradle.kts")

    @BeforeEach
    fun setUp() {
        settingsFile.writeText("""rootProject.name = "test-project"""")
    }

    private fun buildFileWithPlugin(extra: String = "") = """
        plugins {
            java
            id("org.javai.punit")
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation(platform("org.junit:junit-bom:5.14.2"))
            testImplementation("org.junit.jupiter:junit-jupiter")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }
        $extra
    """.trimIndent()

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args)
        .forwardOutput()

    @Nested
    @DisplayName("Plugin Application")
    inner class PluginApplication {

        @Test
        @DisplayName("plugin applies without error")
        fun pluginApplies() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=verification").build()

            assertTrue(result.output.contains("experiment"))
            assertTrue(result.output.contains("exp"))
        }

        @Test
        @DisplayName("experiment and exp tasks are registered")
        fun experimentTasksRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("experiment - Runs experiments"))
            assertTrue(result.output.contains("exp - Shorthand for 'experiment' task"))
        }
    }

    @Nested
    @DisplayName("Test Task Configuration")
    inner class TestTaskConfiguration {

        @Test
        @DisplayName("test task runs successfully with plugin applied")
        fun testTaskRunsSuccessfully() {
            buildFile.writeText(buildFileWithPlugin())

            val testDir = File(projectDir, "src/test/java")
            testDir.mkdirs()
            File(testDir, "DummyTest.java").writeText("""
                import org.junit.jupiter.api.Test;
                class DummyTest {
                    @Test void works() {}
                }
            """.trimIndent())

            val result = runner("test").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        }

        @Test
        @DisplayName("testsubject exclusion can be disabled")
        fun testSubjectExclusionCanBeDisabled() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    excludeTestSubjects.set(false)
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        }

        @Test
        @DisplayName("test task configuration can be disabled entirely")
        fun testTaskConfigurationCanBeDisabled() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    configureTestTask.set(false)
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            // Plugin still registers experiment tasks even when test config is off
            assertTrue(result.output.contains("experiment"))
        }
    }

    @Nested
    @DisplayName("Extension Customization")
    inner class ExtensionCustomization {

        @Test
        @DisplayName("custom output directories are accepted")
        fun customOutputDirs() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    specsDir.set("custom/specs")
                    explorationsDir.set("custom/explorations")
                    optimizationsDir.set("custom/optimizations")
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        }
    }
}
