package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.InputSupplier;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.junit5.testsubjects.FeasibilitySubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

@DisplayName("Feasibility — VERIFICATION fails fast, SMOKE warns")
class FeasibilityIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (savedProperty == null) {
            System.clearProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    @Test
    @DisplayName("VERIFICATION + adequate sample size — feasibility passes; engine runs; verdict PASS")
    void verificationFeasible() throws IOException {
        // n=50 against rate 0.50 is feasible (Wilson at observed=1.0, n=50 ≈ 0.949 > 0.50).
        writeBaselineAt(0.50, 100);

        Events events = run(FeasibilitySubjects.VerificationFeasible.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("VERIFICATION + undersized sample — feasibility fails fast with IllegalStateException")
    void verificationInfeasibleFailsFast() throws IOException {
        // n=10 against rate 0.95 is infeasible (Wilson at observed=1.0, n=10 ≈ 0.787 < 0.95).
        writeBaselineAt(0.95, 1000);

        Events events = run(FeasibilitySubjects.VerificationInfeasible.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    assertThat(throwable.getMessage())
                            .contains("INFEASIBLE VERIFICATION")
                            .contains(FeasibilitySubjects.USE_CASE_ID)
                            .contains("(10)")
                            .contains("At least")
                            .contains("Increase samples")
                            .contains("intent = SMOKE");
                });
    }

    @Test
    @DisplayName("SMOKE + undersized sample — engine runs; warning printed to stderr; verdict still produced")
    void smokeInfeasibleAllowed() throws IOException {
        // Same config as VerificationInfeasible but intent=SMOKE. The check
        // warns instead of failing fast; the engine runs; the verdict is
        // produced as if feasibility had been met.
        writeBaselineAt(0.95, 1000);

        Events events = run(FeasibilitySubjects.SmokeInfeasible.class);
        // The verdict at observed=1.0, n=10 is FAIL (Wilson lower bound 0.787
        // < baseline 0.95). The point of this test is the run wasn't
        // *aborted* — it executed and produced a real verdict (here: FAIL).
        events.assertStatistics(stats -> stats.started(1));
        // It's either succeeded or failed; not aborted (which would mean
        // INCONCLUSIVE), and not skipped (which would mean discovery filter).
        long failedOrSucceeded = events.failed().count() + events.succeeded().count();
        assertThat(failedOrSucceeded)
                .as("SMOKE-intent test runs to verdict; not aborted/skipped")
                .isEqualTo(1);
    }

    private void writeBaselineAt(double rate, int sampleCount) throws IOException {
        BaselineRecord record = new BaselineRecord(
                FeasibilitySubjects.USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new FeasibilitySubjects.NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-04-28T15:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PassRateStatistics(rate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
