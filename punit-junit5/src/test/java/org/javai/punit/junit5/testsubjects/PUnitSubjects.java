package org.javai.punit.junit5.testsubjects;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.engine.criteria.PassRate;
import org.javai.punit.runtime.PUnit;

/**
 * Test subjects for {@link org.javai.punit.junit5.PUnitJunitIntegrationTest}.
 *
 * <p>Lives under {@code testsubjects/} so the project's test-discovery
 * exclusion (per the existing punit convention) keeps these out of
 * normal test runs — they execute only via JUnit Platform TestKit.
 *
 * <p>Two factors-record stand-ins are used: {@link NoFactors} for
 * subjects whose behaviour does not depend on factor values, and
 * {@link GridPoint} for the explore subject, which needs distinct
 * values to populate a grid.
 */
public final class PUnitSubjects {

    private PUnitSubjects() { }

    /**
     * Parameterised factors record for the explore subject — distinct
     * grid points need distinct values.
     */
    public record GridPoint(String label) { }

    private static <F> UseCase<F, Integer, Boolean> alwaysPasses() {
        return new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            // Anonymous-class getSimpleName() is "", which BaselineRecord
            // rejects as a blank useCaseId. Override with a stable id.
            @Override public String id() { return "always-passes-subject"; }
        };
    }

    private static <F> UseCase<F, Integer, Boolean> alwaysFails() {
        return new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.fail("nope", "always fails");
            }
            @Override public String id() { return "always-fails-subject"; }
        };
    }

    private static <F> Sampling<F, Integer, Boolean> sampling(
            UseCase<F, Integer, Boolean> useCase, int samples) {
        return Sampling.<F, Integer, Boolean>builder()
                .useCaseFactory(f -> useCase)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    // ── @ProbabilisticTest subjects ────────────────────────────────

    /** Contractual test that should pass: 100% observed > 50% threshold. */
    public static final class PassingContractualTest {
        @ProbabilisticTest
        void passes() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }

    /** Test that opts in to transparentStats — passes; renderer should emit. */
    public static final class TransparentStatsTest {
        @ProbabilisticTest
        void passesWithTransparentStats() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .transparentStats()
                    .assertPasses();
        }
    }

    /**
     * Passing test that declares a contract reference and opts in to
     * transparentStats — both the rendered verdict and the verbose
     * statistical analysis must surface the reference for audit
     * traceability.
     */
    public static final class ContractRefPassingTest {
        @ProbabilisticTest
        void passesWithContractRef() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .contractRef("Acme API SLA v3.2 §2.1")
                    .transparentStats()
                    .assertPasses();
        }
    }

    /**
     * Failing test that declares a contract reference. The contract
     * reference must appear in the JUnit assertion message so failure
     * triage can trace the threshold back to the document it derives
     * from.
     */
    public static final class ContractRefFailingTest {
        @ProbabilisticTest
        void failsWithContractRef() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysFails(), 20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .contractRef("Acme API SLA v3.2 §2.1")
                    .assertPasses();
        }
    }

    /** Contractual test that should fail: 0% observed < 50% threshold. */
    public static final class FailingContractualTest {
        @ProbabilisticTest
        void fails() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysFails(), 20))
                    .criterion(PassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
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
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 20))
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    // ── @Experiment subjects ───────────────────────────────────────

    /** A measure experiment — produces a baseline; .run() returns normally. */
    public static final class PassingMeasureExperiment {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 100)).run();
        }
    }

    /** An explore experiment — produces a grid; .run() returns normally. */
    public static final class PassingExploreExperiment {
        @Experiment
        void explore() {
            PUnit.exploring(sampling(PUnitSubjects.<GridPoint>alwaysPasses(), 5))
                    .grid(new GridPoint("alt-1"), new GridPoint("alt-2"))
                    .run();
        }
    }

    // ── Empirical-supplier subjects (PUnit.testing(Supplier)) ──────

    /**
     * The baseline supplier — a non-test method returning a built
     * {@link org.javai.punit.api.spec.Experiment} for the
     * {@link PUnit#testing(java.util.function.Supplier)} entry point.
     * The same builder produces both a baseline-running
     * {@code @Experiment} method and the supplier the test consumes.
     */
    public static final class EmpiricalSupplierTest {
        private org.javai.punit.api.spec.Experiment baseline() {
            return PUnit.measuring(sampling(PUnitSubjects.<NoFactors>alwaysPasses(), 100)).build();
        }

        @ProbabilisticTest
        void empiricalDerivedFromBaseline() {
            // No baseline file on disk → BaselineProvider.EMPTY → INCONCLUSIVE.
            // The point of this subject is to exercise the supplier-derivation
            // path: factors and sampling come from baseline(), only samples is
            // specified at the test side.
            PUnit.testing(this::baseline)
                    .samples(20)
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }

    /**
     * Empirical-supplier with a non-MEASURE baseline — must reject
     * with an IllegalArgumentException at compose time.
     */
    public static final class EmpiricalSupplierBadKindTest {
        private org.javai.punit.api.spec.Experiment exploreBaseline() {
            return PUnit.exploring(sampling(PUnitSubjects.<GridPoint>alwaysPasses(), 5))
                    .grid(new GridPoint("alt"))
                    .build();
        }

        @ProbabilisticTest
        void rejectsNonMeasureSupplier() {
            PUnit.testing(this::exploreBaseline)
                    .samples(20)
                    .criterion(PassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }
}
