package org.javai.punit.junit5.testsubjects;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.junit5.PUnit;

/**
 * Subjects for {@code FeasibilityIntegrationTest}. The hosting test
 * pre-populates the configured baseline directory with a hand-written
 * baseline at a known rate, then runs each subject through TestKit.
 *
 * <p>Lives under {@code testsubjects/} so the punit-gradle-plugin's
 * exclude keeps these out of the normal test-discovery sweep.
 */
public final class FeasibilitySubjects {

    private FeasibilitySubjects() { }

    public static final String USE_CASE_ID = "feasibility-use-case";

    public record NoFactors() { }

    private static UseCase<NoFactors, Integer, Boolean> alwaysPasses() {
        return new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                return UseCaseOutcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> alwaysPasses())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * VERIFICATION + adequately sized sample: feasibility check passes,
     * engine runs, criterion produces PASS.
     */
    public static final class VerificationFeasible {
        @ProbabilisticTest
        void shouldPass() {
            // Baseline rate 0.50; n=50 against rate 0.50 has min Wilson
            // lower bound at observed=1.0 ≈ 0.949 — well above 0.50 → feasible.
            PUnit.testing(sampling(50), new NoFactors())
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * VERIFICATION + undersized sample: feasibility check throws
     * IllegalStateException before the engine runs.
     */
    public static final class VerificationInfeasible {
        @ProbabilisticTest
        void shouldFailFast() {
            // Baseline rate 0.95; n=10 against rate 0.95 has max Wilson
            // lower bound at observed=1.0 ≈ 0.787 — below 0.95 → infeasible.
            // Default intent is VERIFICATION → throw IllegalStateException.
            PUnit.testing(sampling(10), new NoFactors())
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * SMOKE + undersized sample: feasibility check warns but allows
     * the engine to run; the test passes (or whatever verdict).
     */
    public static final class SmokeInfeasible {
        @ProbabilisticTest
        void shouldRunWithWarning() {
            // Same configuration as VerificationInfeasible — n=10 against
            // baseline 0.95 — but explicitly SMOKE intent. Run proceeds;
            // a warning is printed to stderr.
            PUnit.testing(sampling(10), new NoFactors())
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .intent(TestIntent.SMOKE)
                    .assertPasses();
        }
    }
}
