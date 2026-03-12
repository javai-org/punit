package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.sentinel.testsubjects.FailingSentinel;
import org.javai.punit.sentinel.testsubjects.NoAnnotationClass;
import org.javai.punit.sentinel.testsubjects.PassingSentinel;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
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
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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

            ProbabilisticTestVerdict verdict = result.verdicts().getFirst();
            assertThat(verdict.junitPassed()).isTrue();
            assertThat(verdict.identity().useCaseId()).hasValue("stub-use-case");
            assertThat(verdict.correlationId()).startsWith("v:");
        }

        @Test
        @DisplayName("executes failing sentinel and returns FAIL verdict")
        void failingSentinel() {
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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

            ProbabilisticTestVerdict verdict = result.verdicts().getFirst();
            assertThat(verdict.junitPassed()).isFalse();
            assertThat(verdict.identity().useCaseId()).hasValue("failing-use-case");
        }

        @Test
        @DisplayName("dispatches verdicts to configured sink")
        void dispatchesToSink() {
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            assertThat(captured).hasSize(1);
            assertThat(captured.getFirst().junitPassed()).isTrue();
        }

        @Test
        @DisplayName("verdict contains execution summary")
        void verdictContainsExecutionSummary() {
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            ProbabilisticTestVerdict verdict = captured.getFirst();
            assertThat(verdict.execution().plannedSamples()).isGreaterThan(0);
            assertThat(verdict.execution().samplesExecuted()).isGreaterThan(0);
            assertThat(verdict.execution().successes()).isGreaterThanOrEqualTo(0);
            assertThat(verdict.execution().observedPassRate()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("includes environment metadata in verdicts")
        void includesEnvironmentMetadata() {
            EnvironmentMetadata metadata = new EnvironmentMetadata("test-env", "instance-1");
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
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
        @DisplayName("records functional dimension in verdict")
        void recordsFunctionalDimension() {
            List<ProbabilisticTestVerdict> captured = new ArrayList<>();
            SentinelConfiguration config = SentinelConfiguration.builder()
                    .sentinelClass(PassingSentinel.class)
                    .verdictSink(captured::add)
                    .build();

            new SentinelRunner(config).runTests();

            ProbabilisticTestVerdict verdict = captured.getFirst();
            assertThat(verdict.functional()).isPresent();
            assertThat(verdict.functional().get().successes()).isGreaterThan(0);
        }
    }
}
