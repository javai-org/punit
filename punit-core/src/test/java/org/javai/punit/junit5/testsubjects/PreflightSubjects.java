package org.javai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.PostconditionBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.internal.engine.criteria.PassRate;
import org.javai.punit.runtime.PUnit;

/**
 * Test subjects for the baseline-existence preflight short-circuit.
 *
 * <p>The service contract carries a static invoke counter. Tests that exercise
 * the short-circuit assert the counter ends at zero — the framework
 * must not have driven the engine when the verdict was structurally
 * guaranteed to be INCONCLUSIVE-no-baseline.
 */
public final class PreflightSubjects {

    public static final String USE_CASE_ID = "preflight-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private PreflightSubjects() { }

    /** Always-passing service contract that records every invocation. */
    private static ServiceContract<NoFactors, Integer, Boolean> countingServiceContract() {
        return new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                INVOKE_COUNT.incrementAndGet();
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> countingServiceContract())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * Empirical test against a baseline directory that holds nothing for
     * this service contract → preflight short-circuit, engine never runs, JUnit
     * aborts.
     */
    public static final class EmpiricalNoBaselineTest {
        @ProbabilisticTest
        void empiricalWithoutBaseline() {
            PUnit.testing(sampling(20))
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Mixed required-empirical (no baseline) + required-contractual
     * criteria. Per the rule "any required empirical criterion missing
     * its baseline short-circuits", the contractual criterion does not
     * get the chance to evaluate — the verdict is structurally
     * INCONCLUSIVE regardless of what the contractual claim would have
     * concluded. Engine never runs.
     */
    public static final class MixedEmpiricalAndContractualNoBaselineTest {
        @ProbabilisticTest
        void mixed() {
            PUnit.testing(sampling(20))
                    .criterion(PassRate.<Boolean>empirical())
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /**
     * Required contractual + report-only empirical (no baseline). Only
     * <em>required</em> empirical criteria short-circuit; a report-only
     * criterion's INCONCLUSIVE is informational. Engine runs.
     */
    public static final class ReportOnlyEmpiricalNoBaselineTest {
        @ProbabilisticTest
        void reportOnlyDoesNotShortCircuit() {
            PUnit.testing(sampling(20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .reportOnly(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Phase 1 of the resolved-baseline regression: stamp a baseline so
     * the paired test below has something to resolve.
     */
    public static final class MeasureBaseline {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(100)).run();
        }
    }

    /**
     * Phase 2 of the resolved-baseline regression: empirical test against
     * a baseline that <em>does</em> exist → preflight does not
     * short-circuit, engine runs.
     */
    public static final class EmpiricalWithBaselineTest {
        @ProbabilisticTest
        void empiricalWithBaseline() {
            // Sample count matches the measure phase (100) so the
            // sample-size constraint holds; baseline rate is 1.0
            // (always-passes), which silently skips feasibility's
            // degenerate-rate case. No .atConfidence(...) override —
            // the framework default sits comfortably above the
            // soundness floor.
            PUnit.testing(sampling(100))
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }
}
