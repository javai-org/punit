package org.javai.punit.junit5.testsubjects;

import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.junit5.PUnit;

/**
 * Subjects for {@code MeasureToTestRoundTripTest}.
 *
 * <p>Lives under {@code testsubjects/} so the punit-gradle-plugin's
 * {@code exclude("**}{@code /testsubjects/**")} keeps these out of the
 * normal test-discovery sweep. The hosting test sets the baseline-
 * directory system property in {@code @BeforeEach} and runs each
 * subject through {@link org.junit.platform.testkit.engine.EngineTestKit}.
 */
public final class RoundTripSubjects {

    private RoundTripSubjects() { }

    public static final String USE_CASE_ID = "round-trip-use-case";

    public record NoFactors() { }

    private static UseCase<NoFactors, Integer, Boolean> useCase() {
        return new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                return UseCaseOutcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> useCase())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    public static final class PassingMeasure {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(50), new NoFactors())
                    .experimentId("baseline-v1")
                    .run();
        }
    }

    public static final class EmpiricalAgainstBaseline {
        @ProbabilisticTest
        void shouldPass() {
            PUnit.testing(sampling(20), new NoFactors())
                    .criterion(BernoulliPassRate.<Boolean>empirical())
                    .assertPasses();
        }
    }
}
