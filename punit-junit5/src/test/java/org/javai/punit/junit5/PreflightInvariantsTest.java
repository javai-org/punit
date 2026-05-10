package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.InputSupplier;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineResolver;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.junit5.testsubjects.PreflightInvariantSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Audits each pre-flight invariant the framework upholds, end-to-end
 * through the typed authoring surface. Each test constructs a
 * configuration that should trip the invariant (or be feasible by
 * construction, in PT03's case) and asserts the framework's response —
 * including, where the gate aborts, that no samples ran.
 *
 * <p>The directive {@code DIR-BUG-FEASIBILITY-VERIFICATION-punit}
 * Part 3 motivates this class: the original regression slipped past
 * existing unit tests because the gate was wired only at the
 * evaluator level, not exercised end-to-end against the
 * {@code PUnit.testing(...).criterion(...).assertPasses()} surface.
 * One test per audited invariant guards against the same shape of
 * regression in the future.
 *
 * <p>PT08 soundness floor is intentionally absent — the audit
 * recorded it as a gap (configurations whose configured confidence
 * falls below 80% should abort regardless of intent; the framework
 * does not enforce this today). Implementing the floor would force
 * reworks in {@code CovariateRoundTripTest} and {@code PreflightSubjects}
 * — two classes that use {@code .atConfidence(0.50)} as a workaround
 * to make small-n configurations feasible. The directive's
 * "do not silently expand scope" instruction makes that a follow-up
 * directive, not a fix folded into this PR.
 */
@DisplayName("Pre-flight invariants — end-to-end audit (PT01 / PT02 / PT03 / PT12 / PT13)")
class PreflightInvariantsTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        PreflightInvariantSubjects.INVOKE_COUNT.set(0);
    }

    @AfterEach
    void tearDown() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    @Test
    @DisplayName("PT01: declared (samples, threshold) infeasible under VERIFICATION → abort, no samples")
    void pt01ThresholdFirstAbortsBeforeSampling() {
        Events events = run(
                PreflightInvariantSubjects.PT01ThresholdFirstInfeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertInfeasibilityException(events);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("PT01 abort must precede sampling — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("PT02: declared (samples, confidence) + empirical baseline rate too high → abort, no samples")
    void pt02SampleSizeFirstAbortsBeforeSampling() throws IOException {
        // Hand-write a baseline at rate 0.95: the always-passing use
        // case would have produced rate 1.0, which Feasibility skips
        // as degenerate. The PT02 invariant fires when an
        // empirical-derived threshold sits above what the configured
        // sample size can underwrite.
        writeBaselineAt(0.95, 1000);

        Events events = run(
                PreflightInvariantSubjects.PT02SampleSizeFirstInfeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertInfeasibilityException(events);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("PT02 abort must precede sampling — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("PT03: PowerAnalysis-derived sample count is feasible by construction → engine runs")
    void pt03ConfidenceFirstFeasibleByConstruction() {
        // Phase 1: seed the baseline PowerAnalysis will read from.
        run(PreflightInvariantSubjects.PT03BaselineMeasure.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));
        int afterMeasure = PreflightInvariantSubjects.INVOKE_COUNT.get();

        // Phase 2: the empirical test asks PowerAnalysis for a sized
        // sample count and configures itself with that. The
        // configuration is feasible by construction; the engine runs.
        Events events = run(
                PreflightInvariantSubjects.PT03ConfidenceFirstFeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1));
        long terminal = events.failed().count() + events.succeeded().count();
        assertThat(terminal)
                .as("PT03 feasible-by-construction config runs to a verdict, not aborted")
                .isEqualTo(1);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("PT03 engine runs the configured number of samples")
                .isGreaterThan(afterMeasure);
    }

    @Test
    @DisplayName("PT12: out-of-range threshold rejected at construction → IllegalArgumentException")
    void pt12ParameterValidationRejectsOutOfRange() {
        Events events = run(
                PreflightInvariantSubjects.PT12ParameterValidationTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    Throwable t = event.getRequiredPayload(TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(t).isInstanceOf(IllegalArgumentException.class);
                    assertThat(t.getMessage()).contains("threshold").contains("[0, 1]");
                });
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("PT12 rejection precedes any framework wiring — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("PT13: incoherent ThresholdOrigin.EMPIRICAL on contractual factory → rejected at construction")
    void pt13ConfigurationCoherenceRejectsEmpiricalOnContractual() {
        Events events = run(
                PreflightInvariantSubjects.PT13ConfigurationCoherenceTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    Throwable t = event.getRequiredPayload(TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(t).isInstanceOf(IllegalArgumentException.class);
                    assertThat(t.getMessage())
                            .contains("EMPIRICAL")
                            .contains("empirical factories");
                });
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("PT13 rejection precedes any framework wiring — engine never runs")
                .isZero();
    }

    private void writeBaselineAt(double rate, int sampleCount) throws IOException {
        BaselineRecord record = new BaselineRecord(
                PreflightInvariantSubjects.USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PassRateStatistics(rate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    private static void assertInfeasibilityException(Events events) {
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    Throwable t = event.getRequiredPayload(TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                    assertThat(t.getMessage())
                            .contains("INFEASIBLE VERIFICATION")
                            .contains(PreflightInvariantSubjects.USE_CASE_ID)
                            .contains("Increase samples")
                            .contains("intent = SMOKE");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
