package org.javai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.engine.criteria.PassRate;
import org.javai.punit.power.PowerAnalysis;
import org.javai.punit.runtime.PUnit;

/**
 * Test subjects for {@code PreflightInvariantsTest}, which audits each
 * pre-flight invariant the framework upholds (PT01 / PT02 / PT03 / PT12
 * / PT13). The hosting test counts samples actually executed via
 * {@link #INVOKE_COUNT} so the abort-before-sampling guarantee can be
 * asserted directly.
 *
 * <p>PT08 soundness floor is intentionally not exercised here — see
 * {@code DIR-BUG-FEASIBILITY-VERIFICATION-punit} for the audit
 * outcome (gap; deferred to a follow-up directive).
 */
public final class PreflightInvariantSubjects {

    public static final String USE_CASE_ID = "preflight-invariant-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private PreflightInvariantSubjects() { }

    private static UseCase<NoFactors, Integer, Boolean> countingAlwaysPasses() {
        return new UseCase<>() {
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
                .useCaseFactory(f -> countingAlwaysPasses())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * PT01 — declared (samples, threshold) pair under a normative
     * origin. The configured sample size is too small to underwrite
     * the declared threshold at the default confidence; the
     * pre-flight gate must abort before the engine runs any samples.
     */
    public static final class PT01ThresholdFirstInfeasibleTest {
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
     * PT02 — declared (samples, confidence) pair against an empirical
     * baseline. The baseline rate is too high relative to the
     * configured sample size; the criterion would derive a threshold
     * the test cannot underwrite. Pre-flight aborts before sampling.
     */
    public static final class PT02SampleSizeFirstInfeasibleTest {
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
     * PT03 — confidence-first via {@code PowerAnalysis.sampleSize}
     * against a real baseline. The framework returns a sample count
     * sized for the requested (mde, power) tuple; using that count
     * configures the test to be feasible by construction. The
     * pre-flight gate does not fire and the engine runs.
     */
    public static final class PT03ConfidenceFirstFeasibleTest {

        public static final String EXPERIMENT_ID = "pt03-baseline";

        private org.javai.punit.api.spec.Experiment baseline() {
            return PUnit.measuring(sampling(200))
                    .experimentId(EXPERIMENT_ID)
                    .build();
        }

        @ProbabilisticTest
        void confidenceFirstSampleSizeFeasible() {
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
     * Companion @Experiment for PT03's baseline. The hosting test
     * runs this first to seed the baseline, then runs
     * {@link PT03ConfidenceFirstFeasibleTest} which consumes it.
     */
    public static final class PT03BaselineMeasure {
        @org.javai.punit.api.Experiment
        void seedBaseline() {
            PUnit.measuring(sampling(200))
                    .experimentId(PT03ConfidenceFirstFeasibleTest.EXPERIMENT_ID)
                    .run();
        }
    }

    /**
     * PT12 — parameter validation at construction time. The framework
     * rejects out-of-range parameter values with
     * {@link IllegalArgumentException} before any pre-flight gate
     * runs. This subject demonstrates the catch in a TestKit-driven
     * frame: the {@code @ProbabilisticTest} method itself throws on
     * invocation.
     */
    public static final class PT12ParameterValidationTest {
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
     * PT13 — configuration coherence. The framework rejects
     * incoherent combinations at the criterion factory: passing
     * {@code ThresholdOrigin.EMPIRICAL} to {@link PassRate#meeting}
     * is a category error (EMPIRICAL is reserved for the empirical
     * factory), and the criterion throws on construction.
     */
    public static final class PT13ConfigurationCoherenceTest {
        @ProbabilisticTest
        void rejectsEmpiricalOriginOnContractualFactory() {
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.EMPIRICAL))
                    .assertPasses();
        }
    }
}
