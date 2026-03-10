package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelMainTest {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsing {

        @Test
        @DisplayName("exits with usage error when no arguments provided")
        void noArguments() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("exits with usage error for unknown command")
        void unknownCommand() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"foo"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("accepts 'test' command")
        void testCommand(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("accepts 'exp' command with spec dir")
        void expCommand(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            new SentinelMain(new String[]{"exp"}, system).run();

            // exp runs experiments — may pass or fail, but should not be a USAGE error
            assertThat(system.exitCode).isNotEqualTo(SentinelMain.EXIT_USAGE);
        }
    }

    @Nested
    @DisplayName("experiment spec dir validation")
    class ExperimentSpecDirValidation {

        @Test
        @DisplayName("requires spec dir for experiment mode")
        void requiresSpecDirForExp() {
            TestSystemBridge system = new TestSystemBridge();
            // No spec dir set, no env var

            new SentinelMain(new String[]{"exp"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("accepts spec dir from system property")
        void specDirFromSystemProperty(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            new SentinelMain(new String[]{"exp"}, system).run();

            assertThat(system.exitCode).isNotEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("accepts spec dir from environment variable")
        void specDirFromEnvVar(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");
            system.envVars.put("PUNIT_SPEC_DIR", tempDir.toString());

            new SentinelMain(new String[]{"exp"}, system).run();

            assertThat(system.exitCode).isNotEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("does not require spec dir for test mode")
        void testModeDoesNotRequireSpecDir(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");
            // No spec dir set — should be fine for tests

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isNotEqualTo(SentinelMain.EXIT_USAGE);
        }
    }

    @Nested
    @DisplayName("sentinel class discovery")
    class SentinelClassDiscovery {

        @Test
        @DisplayName("exits with usage error when no manifest found")
        void noManifest() {
            TestSystemBridge system = new TestSystemBridge();
            // Default classloader has no manifest resource

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("loads classes from manifest resource")
        void loadsFromManifest(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            SentinelMain main = new SentinelMain(new String[]{"test"}, system);
            List<Class<?>> classes = main.loadSentinelClasses();

            assertThat(classes).hasSize(1);
            assertThat(classes.getFirst().getSimpleName()).isEqualTo("PassingSentinel");
        }

        @Test
        @DisplayName("ignores blank lines and comments in manifest")
        void ignoresBlanksAndComments(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "# This is a comment\n" +
                    "\n" +
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel\n" +
                    "  \n");

            SentinelMain main = new SentinelMain(new String[]{"test"}, system);
            List<Class<?>> classes = main.loadSentinelClasses();

            assertThat(classes).hasSize(1);
        }

        @Test
        @DisplayName("exits with usage error for unknown class in manifest")
        void unknownClassInManifest(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "com.nonexistent.FakeClass");

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }
    }

    @Nested
    @DisplayName("exit codes")
    class ExitCodes {

        @Test
        @DisplayName("returns success when all tests pass")
        void successWhenAllPass(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("returns failure when any test fails")
        void failureWhenTestFails(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.FailingSentinel");

            new SentinelMain(new String[]{"test"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_FAILURE);
        }
    }

    @Nested
    @DisplayName("verbose flag")
    class VerboseFlag {

        @Test
        @DisplayName("--verbose flag produces per-verdict output")
        void verboseFlagProducesVerdictOutput(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"test", "--verbose"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("VERDICT:");
            assertThat(output).contains("Observed pass rate:");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("--verbose flag works before command")
        void verboseFlagBeforeCommand(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"--verbose", "test"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("VERDICT:");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("without --verbose, no per-verdict framing is produced")
        void withoutVerboseNoVerdictFraming(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"test"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            // Without verbose, the output should have the summary table but not per-verdict framing
            assertThat(output).contains("Sentinel Test Summary");
            assertThat(output).doesNotContain("VERDICT: PASS");
        }

        @Test
        @DisplayName("--verbose experiment shows per-sample progress")
        void verboseExperimentShowsSampleProgress(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.ExperimentSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"exp", "--verbose"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("ExperimentSentinel.measureStub");
            assertThat(output).contains("5 samples");
            assertThat(output).contains("sample 1/5");
            assertThat(output).contains("sample 5/5");
            assertThat(output).contains("-> done (5/5 samples passed)");
        }

        @Test
        @DisplayName("--verbose experiment does not show verdict framing")
        void verboseExperimentDoesNotShowVerdictFraming(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.ExperimentSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"exp", "--verbose"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).doesNotContain("VERDICT:");
            assertThat(output).doesNotContain("Observed pass rate:");
            assertThat(output).doesNotContain("Result: PASS");
            assertThat(output).doesNotContain("Result: FAIL");
        }

        @Test
        @DisplayName("experiment without --verbose shows no sample progress")
        void experimentWithoutVerboseShowsNoSampleProgress(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.ExperimentSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"exp"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Sentinel Experiment Summary");
            assertThat(output).contains("Result: COMPLETE");
            assertThat(output).contains("Samples:");
            assertThat(output).doesNotContain("sample 1/");
            assertThat(output).doesNotContain("Result: PASS");
        }
    }

    @Nested
    @DisplayName("console output")
    class ConsoleOutput {

        @Test
        @DisplayName("prints summary with pass/fail counts")
        void printsSummary(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"test"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Sentinel Test Summary");
            assertThat(output).contains("Passed:");
            assertThat(output).contains("Result: PASS");
        }

        @Test
        @DisplayName("prints failed test names on failure")
        void printsFailedTests(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.FailingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"test"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Result: FAIL");
            assertThat(output).contains("FAILED:");
        }
    }

    @Nested
    @DisplayName("--help")
    class Help {

        @Test
        @DisplayName("--help prints help and exits with success")
        void helpFlag() {
            TestSystemBridge system = new TestSystemBridge();

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"--help"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Commands:");
            assertThat(output).contains("test");
            assertThat(output).contains("exp");
            assertThat(output).contains("--verbose");
            assertThat(output).contains("--useCase");
            assertThat(output).contains("--list");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("-h is a shorthand for --help")
        void shortHelpFlag() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"-h"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }
    }

    @Nested
    @DisplayName("--list")
    class ListUseCases {

        @Test
        @DisplayName("lists test and experiment use cases")
        void listsUseCases(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel\n" +
                    "org.javai.punit.sentinel.testsubjects.ExperimentSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(new String[]{"--list"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Use Case Id");
            assertThat(output).contains("stub-use-case");
            assertThat(output).contains("test");
            assertThat(output).contains("experiment");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("--list does not require a command")
        void listWithoutCommand(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            new SentinelMain(new String[]{"--list"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }
    }

    @Nested
    @DisplayName("--useCase")
    class UseCaseFilter {

        @Test
        @DisplayName("runs only the specified test use case")
        void filtersTestByUseCase(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(
                        new String[]{"test", "--useCase", "stub-use-case"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Total:    1");
            assertThat(output).contains("Passed:   1");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("non-matching use case produces zero results")
        void nonMatchingUseCase(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.PassingSentinel");

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(
                        new String[]{"test", "--useCase", "nonexistent"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Total:    0");
            assertThat(output).contains("Result: NO MATCH");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_FAILURE);
        }

        @Test
        @DisplayName("filters experiments by use case")
        void filtersExperimentByUseCase(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    "org.javai.punit.sentinel.testsubjects.ExperimentSentinel");
            system.properties.put("punit.spec.dir", tempDir.toString());

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                new SentinelMain(
                        new String[]{"exp", "--useCase", "stub-use-case"}, system).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("Result: COMPLETE");
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("--useCase without value shows help")
        void useCaseWithoutValue() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"test", "--useCase"}, system).run();

            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS); // treated as --help
        }
    }

    // ── Test infrastructure ──────────────────────────────────────────────

    /**
     * Creates a TestSystemBridge with a classloader that includes a
     * sentinel-classes manifest pointing to the given class names.
     */
    private TestSystemBridge systemWithManifest(Path tempDir, String manifestContent)
            throws IOException {
        Path metaInf = tempDir.resolve("META-INF").resolve("punit");
        Files.createDirectories(metaInf);
        Files.writeString(metaInf.resolve("sentinel-classes"), manifestContent);

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{tempDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader());

        TestSystemBridge system = new TestSystemBridge();
        system.classLoader = classLoader;
        return system;
    }

    private static class TestSystemBridge implements SentinelMain.SystemBridge {
        int exitCode = -1;
        final Map<String, String> properties = new HashMap<>();
        final Map<String, String> envVars = new HashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        @Override
        public void exit(int code) {
            this.exitCode = code;
        }

        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public String getenv(String name) {
            return envVars.get(name);
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
