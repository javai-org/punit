package org.javai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.internal.engine.criteria.PassRate;
import org.javai.punit.internal.engine.baseline.PowerAnalysis;
import org.javai.punit.runtime.PUnit;

/**
 * Test subjects for {@code PreflightInvariantsTest}, which audits the
 * pre-flight invariants the framework upholds end-to-end through the
 * typed authoring surface — declared-threshold feasibility,
 * declared-sample-size feasibility, power-analysis-derived feasibility,
 * parameter validation, and configuration coherence. The hosting test
 * counts samples actually executed via {@link #INVOKE_COUNT} so the
 * abort-before-sampling guarantee can be asserted directly.
 *
 * <p>The soundness floor (≥ 80% confidence regardless of intent) is
 * intentionally not exercised here — the audit recorded it as a gap
 * tracked in a separate orchestrator directive.
 */
public final class PreflightInvariantSubjects {

    public static final String USE_CASE_ID = "preflight-invariant-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private PreflightInvariantSubjects() { }

    private static ServiceContract<NoFactors, Integer, Boolean> countingAlwaysPasses() {
        return new ServiceContract<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                INVOKE_COUNT.incrementAndGet();
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> countingAlwaysPasses())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * Declared (samples, threshold) pair under a normative origin.
     * The configured sample size is too small to underwrite the
     * declared threshold at the default confidence; the pre-flight
     * gate must abort before the engine runs any samples.
     */
    public static final class DeclaredThresholdInfeasibleTest {
        @ProbabilisticTest
        void undersizedAgainstNormativeThreshold() {
            // n=50 against 0.9999 at 95% confidence: Wilson upper-of-perfect
            // ≈ 0.949, well below 0.9999 → infeasible.
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(0.9999, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /**
     * Declared (samples, confidence) pair against an empirical
     * baseline. The baseline rate is too high relative to the
     * configured sample size; the criterion would derive a threshold
     * the test cannot underwrite. Pre-flight aborts before sampling.
     */
    public static final class DeclaredSampleSizeInfeasibleTest {
        @ProbabilisticTest
        void undersizedAgainstHighBaselineRate() {
            // n=10 against baseline 0.95 at default 95% confidence:
            // Wilson upper-of-perfect ≈ 0.787 < 0.95 → infeasible.
            PUnit.testing(sampling(10))
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Power-analysis-derived sample size against a real baseline.
     * The framework returns a sample count sized for the requested
     * (mde, power) tuple; using that count configures the test to be
     * feasible by construction. The pre-flight gate does not fire and
     * the engine runs.
     */
    public static final class PowerAnalysisDerivedFeasibleTest {

        public static final String EXPERIMENT_ID = "power-analysis-baseline";

        private org.javai.punit.api.spec.Experiment baseline() {
            return PUnit.measuring(sampling(200))
                    .experimentId(EXPERIMENT_ID)
                    .build();
        }

        @ProbabilisticTest
        void powerAnalysisSampleSizeIsFeasible() {
            // PowerAnalysis derives n from (baseline rate, mde, power);
            // the test then runs with that n. By construction the
            // (n, baseline-rate, default-confidence) tuple is feasible.
            int n = PowerAnalysis.sampleSize(this::baseline, 0.05, 0.80);
            PUnit.testing(sampling(n))
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Companion @Experiment for the power-analysis baseline. The
     * hosting test runs this first to seed the baseline, then runs
     * {@link PowerAnalysisDerivedFeasibleTest} which consumes it.
     */
    public static final class PowerAnalysisBaselineMeasure {
        @org.javai.punit.api.Experiment
        void seedBaseline() {
            PUnit.measuring(sampling(200))
                    .experimentId(PowerAnalysisDerivedFeasibleTest.EXPERIMENT_ID)
                    .run();
        }
    }

    /**
     * Parameter validation at construction time. The framework
     * rejects out-of-range parameter values with
     * {@link IllegalArgumentException} before any pre-flight gate
     * runs. This subject demonstrates the catch in a TestKit-driven
     * frame: the {@code @ProbabilisticTest} method itself throws on
     * invocation.
     */
    public static final class ParameterValidationTest {
        @ProbabilisticTest
        void rejectsOutOfRangeThreshold() {
            // PassRate.meeting validates threshold ∈ [0, 1] at
            // construction; -0.1 throws before .assertPasses() is ever
            // called.
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(-0.1, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /**
     * Configuration coherence. The framework rejects incoherent
     * combinations at the criterion factory: passing
     * {@code ThresholdOrigin.EMPIRICAL} to {@link PassRate#meeting}
     * is a category error (EMPIRICAL is reserved for the empirical
     * factory), and the criterion throws on construction.
     */
    public static final class ConfigurationCoherenceTest {
        @ProbabilisticTest
        void rejectsEmpiricalOriginOnContractualFactory() {
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.EMPIRICAL))
                    .assertPasses();
        }
    }
}
