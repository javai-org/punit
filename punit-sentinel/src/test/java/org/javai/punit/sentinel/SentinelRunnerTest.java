package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.reporting.VerdictEvent;
import org.javai.punit.sentinel.testsubjects.FailingSentinel;
import org.javai.punit.sentinel.testsubjects.NoAnnotationClass;
import org.javai.punit.sentinel.testsubjects.PassingSentinel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelRunnerTest {

    @Nested
    @DisplayName("runTests")
    class RunTests {

        @Test
        @DisplayName("executes passing sentinel and returns PASS verdict")
        void passingSentinel() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            SentinelRunner runner = new SentinelRunner(config);
            SentinelResult result = runner.runTests();

            assertThat(result.allPassed()).isTrue();
            assertThat(result.totalTests()).isEqualTo(1);
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(0);
            assertThat(result.verdicts()).hasSize(1);

            VerdictEvent verdict = result.verdicts().getFirst();
            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.useCaseId()).isEqualTo("stub-use-case");
            assertThat(verdict.correlationId()).startsWith("v:");
        }

        @Test
        @DisplayName("executes failing sentinel and returns FAIL verdict")
        void failingSentinel() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(FailingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            SentinelRunner runner = new SentinelRunner(config);
            SentinelResult result = runner.runTests();

            assertThat(result.allPassed()).isFalse();
            assertThat(result.totalTests()).isEqualTo(1);
            assertThat(result.passed()).isEqualTo(0);
            assertThat(result.failed()).isEqualTo(1);

            VerdictEvent verdict = result.verdicts().getFirst();
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.useCaseId()).isEqualTo("failing-use-case");
        }

        @Test
        @DisplayName("dispatches verdicts to configured sink")
        void dispatchesToSink() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            assertThat(captured).hasSize(1);
            assertThat(captured.getFirst().passed()).isTrue();
        }

        @Test
        @DisplayName("report entries contain expected keys")
        void reportEntriesContainExpectedKeys() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            var entries = captured.getFirst().reportEntries();
            assertThat(entries).containsKeys(
                    "punit.samples", "punit.samplesExecuted",
                    "punit.successes", "punit.failures",
                    "punit.minPassRate", "punit.observedPassRate",
                    "punit.verdict", "punit.terminationReason",
                    "punit.elapsedMs");
        }

        @Test
        @DisplayName("includes environment metadata in verdicts")
        void includesEnvironmentMetadata() {
            EnvironmentMetadata metadata = new EnvironmentMetadata("test-env", "instance-1");
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .environmentMetadata(metadata)
                    .build();

            new SentinelRunner(config).runTests();

            var envMap = captured.getFirst().environmentMetadata();
            assertThat(envMap).containsEntry("environment", "test-env");
            assertThat(envMap).containsEntry("instance", "instance-1");
        }

        @Test
        @DisplayName("filters tests by use case ID")
        void filtersByUseCaseId() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            SentinelResult result = new SentinelRunner(config).runTests("non-existent-use-case");

            assertThat(result.totalTests()).isEqualTo(0);
            assertThat(captured).isEmpty();
        }

        @Test
        @DisplayName("runs specific use case when filtered")
        void runsSpecificUseCase() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            SentinelResult result = new SentinelRunner(config).runTests("stub-use-case");

            assertThat(result.totalTests()).isEqualTo(1);
            assertThat(captured).hasSize(1);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects class without @Sentinel annotation")
        void rejectsNonSentinelClass() {
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(NoAnnotationClass.class)
                    .build();

            SentinelRunner runner = new SentinelRunner(config);
            assertThatThrownBy(runner::runTests)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not annotated with @Sentinel");
        }
    }

    @Nested
    @DisplayName("multiple sentinel classes")
    class MultipleSentinelClasses {

        @Test
        @DisplayName("runs tests from multiple sentinel classes")
        void runsMultipleClasses() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .sentinelClass(FailingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            SentinelResult result = new SentinelRunner(config).runTests();

            assertThat(result.totalTests()).isEqualTo(2);
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(captured).hasSize(2);
        }
    }

    @Nested
    @DisplayName("per-dimension tracking")
    class PerDimensionTracking {

        @Test
        @DisplayName("records functional dimension in report entries")
        void recordsFunctionalDimension() {
            List<VerdictEvent> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            var entries = captured.getFirst().reportEntries();
            assertThat(entries).containsEntry("punit.dimension.functional", "true");
            assertThat(entries).containsKey("punit.dimension.functional.successes");
        }
    }
}
