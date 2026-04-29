package org.javai.punit.junit5.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.junit5.PUnit;

/**
 * Test subjects for {@link org.javai.punit.junit5.CovariateRoundTripTest}.
 *
 * <p>The use case declares one custom covariate ({@code region}) whose
 * resolver reads a system property. The test sets the property in
 * {@code @BeforeEach}, runs a measure to stamp a baseline with the
 * resolved value, then runs an empirical test that should resolve
 * the matching baseline.
 */
public final class CovariateSubjects {

    public static final String USE_CASE_ID = "covariate-subject";
    public static final String REGION_PROPERTY = "punit.test.region";

    private CovariateSubjects() { }

    public record NoFactors() { }

    /**
     * A use case that always passes and declares a single CONFIGURATION
     * covariate read from {@link #REGION_PROPERTY}. Hard-gating
     * CONFIGURATION means a test under {@code region=APAC} cannot
     * silently fall back to a baseline measured under {@code region=EU}.
     */
    private static UseCase<NoFactors, Integer, Boolean> covariateUseCase() {
        return new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                return UseCaseOutcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }

            @Override
            public List<Covariate> covariates() {
                return List.of(Covariate.custom("region",
                        CovariateCategory.CONFIGURATION));
            }

            @Override
            public Map<String, Supplier<String>> customCovariateResolvers() {
                return Map.of("region",
                        () -> System.getProperty(REGION_PROPERTY, "UNSET"));
            }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> covariateUseCase())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /** Phase 1: stamp a baseline under whatever value REGION_PROPERTY holds. */
    public static final class MeasureWithCovariate {
        @Experiment
        void measureBaseline() {
            PUnit.measuring(sampling(100), new NoFactors())
                    .experimentId("measureBaseline")
                    .run();
        }
    }

    /**
     * Phase 2: run the empirical test. Resolution finds the baseline
     * stamped with the matching covariate; criterion produces PASS
     * when match found, INCONCLUSIVE when no match (the
     * "different region" scenario in the round-trip test).
     */
    public static final class TestWithMatchingCovariate {
        @ProbabilisticTest
        void shouldMatchBaseline() {
            // Wilson lower bound at observed=1.0, n=20, c=0.95 ≈ 0.83;
            // baseline observed rate is 1.0 → bound 0.83 < threshold
            // 1.0 → would FAIL on a strict-rate baseline. We instead
            // assert resolution-time correctness: when the baseline
            // matches we get a real verdict (not INCONCLUSIVE), and
            // when it doesn't we get INCONCLUSIVE.
            PUnit.testing(sampling(20), new NoFactors())
                    .criterion(BernoulliPassRate.<Boolean>empirical()
                            .atConfidence(0.50))
                    .assertPasses();
        }
    }
}
