package org.javai.punit.junit5.testsubjects;

import org.javai.punit.api.PunitTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.ProbabilisticTest;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.junit5.Punit;

/**
 * Test subjects for {@link org.javai.punit.junit5.PunitJunitIntegrationTest}.
 *
 * <p>Lives under {@code testsubjects/} so the project's test-discovery
 * exclusion (per the existing punit convention) keeps these out of
 * normal test runs — they execute only via JUnit Platform TestKit.
 */
public final class PunitTestSubjects {

    private PunitTestSubjects() { }

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
            UseCase<Factors, Integer, Boolean> useCase) {
        return Sampling.<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> useCase)
                .inputs(1, 2, 3)
                .samples(20)
                .build();
    }

    /** Contractual test that should pass: 100% observed > 50% threshold. */
    public static final class PassingTest {
        @PunitTest
        void passes() {
            Punit.run(ProbabilisticTest
                    .testing(sampling(ALWAYS_PASSES), new Factors("m"))
                    .criterion(BernoulliPassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .build());
        }
    }

    /** Contractual test that should fail: 0% observed < 50% threshold. */
    public static final class FailingTest {
        @PunitTest
        void fails() {
            Punit.run(ProbabilisticTest
                    .testing(sampling(ALWAYS_FAILS), new Factors("m"))
                    .criterion(BernoulliPassRate.<Boolean>meeting(0.5, ThresholdOrigin.SLA))
                    .build());
        }
    }

    /**
     * Empirical test against the EMPTY baseline provider — no
     * baseline resolves, criterion yields INCONCLUSIVE, JUnit
     * sees TestAbortedException → aborted test.
     */
    public static final class InconclusiveEmpiricalTest {
        @PunitTest
        void inconclusive() {
            Punit.run(ProbabilisticTest
                    .testing(sampling(ALWAYS_PASSES), new Factors("m"))
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .build());
        }
    }
}
