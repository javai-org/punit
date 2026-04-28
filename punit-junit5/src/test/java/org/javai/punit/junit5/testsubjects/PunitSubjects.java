package org.javai.punit.junit5.testsubjects;

import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.junit5.Punit;

/**
 * Test subjects for {@link org.javai.punit.junit5.PunitJunitIntegrationTest}.
 *
 * <p>Lives under {@code testsubjects/} so the project's test-discovery
 * exclusion (per the existing punit convention) keeps these out of
 * normal test runs — they execute only via JUnit Platform TestKit.
 */
public final class PunitSubjects {

    private PunitSubjects() { }

    public record Factors(String label) { }

    private static final UseCase<Factors, Integer, Boolean> ALWAYS_PASSES = new UseCase<>() {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.ok(true);
        }
    };

    private static final UseCase<Factors, Integer, Boolean> ALWAYS_FAILS = new UseCase<>() {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.fail("nope", "always fails");
        }
    };

    private static Sampling<Factors, Integer, Boolean> sampling(
            UseCase<Factors, Integer, Boolean> useCase, int samples) {
        return Sampling.<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> useCase)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static final Factors FACTORS = new Factors("m");

    // ── @ProbabilisticTest subjects ────────────────────────────────

    /** Contractual test that should pass: 100% observed > 50% threshold. */
    public static final class PassingContractualTest {
        @ProbabilisticTest
        void passes() {
            Punit.testing(sampling(ALWAYS_PASSES, 20), FACTORS)
                    .criterion(BernoulliPassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /** Contractual test that should fail: 0% observed < 50% threshold. */
    public static final class FailingContractualTest {
        @ProbabilisticTest
        void fails() {
            Punit.testing(sampling(ALWAYS_FAILS, 20), FACTORS)
                    .criterion(BernoulliPassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /**
     * Empirical test against the EMPTY baseline provider — no
     * baseline resolves, criterion yields INCONCLUSIVE → JUnit
     * aborted (skipped) test.
     */
    public static final class InconclusiveEmpiricalTest {
        @ProbabilisticTest
        void inconclusive() {
            Punit.testing(sampling(ALWAYS_PASSES, 20), FACTORS)
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    // ── @Experiment subjects ───────────────────────────────────────

    /** A measure experiment — produces a baseline; .run() returns normally. */
    public static final class PassingMeasureExperiment {
        @Experiment
        void measure() {
            Punit.measuring(sampling(ALWAYS_PASSES, 100), FACTORS).run();
        }
    }

    /** An explore experiment — produces a grid; .run() returns normally. */
    public static final class PassingExploreExperiment {
        @Experiment
        void explore() {
            Punit.exploring(sampling(ALWAYS_PASSES, 5))
                    .grid(new Factors("a"), new Factors("b"))
                    .run();
        }
    }

    // ── Empirical-supplier subjects (Punit.testing(Supplier)) ──────

    /**
     * The baseline supplier — a non-test method returning a built
     * {@link org.javai.punit.api.typed.spec.Experiment} for the
     * {@link Punit#testing(java.util.function.Supplier)} entry point.
     * The same builder produces both a baseline-running
     * {@code @Experiment} method and the supplier the test consumes.
     */
    public static final class EmpiricalSupplierTest {
        private org.javai.punit.api.typed.spec.Experiment baseline() {
            return Punit.measuring(sampling(ALWAYS_PASSES, 100), FACTORS).build();
        }

        @ProbabilisticTest
        void empiricalDerivedFromBaseline() {
            // No baseline file on disk → BaselineProvider.EMPTY → INCONCLUSIVE.
            // The point of this subject is to exercise the supplier-derivation
            // path: factors and sampling come from baseline(), only samples is
            // specified at the test side.
            Punit.testing(this::baseline)
                    .samples(20)
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Empirical-supplier with a non-MEASURE baseline — must reject
     * with an IllegalArgumentException at compose time.
     */
    public static final class EmpiricalSupplierBadKindTest {
        private org.javai.punit.api.typed.spec.Experiment exploreBaseline() {
            return Punit.exploring(sampling(ALWAYS_PASSES, 5))
                    .grid(FACTORS)
                    .build();
        }

        @ProbabilisticTest
        void rejectsNonMeasureSupplier() {
            Punit.testing(this::exploreBaseline)
                    .samples(20)
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }
}
