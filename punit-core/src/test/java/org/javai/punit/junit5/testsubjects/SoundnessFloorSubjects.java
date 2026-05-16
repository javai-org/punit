package org.javai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.PostconditionBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.internal.engine.criteria.PassRate;
import org.javai.punit.runtime.PUnit;

/**
 * Subjects for {@code SoundnessFloorTest}. The framework enforces a
 * minimum confidence level (the "soundness floor") regardless of
 * declared {@link TestIntent}: a test configured below the floor
 * cannot underwrite a verdict, and Smoke does not buy past that.
 *
 * <p>The hosting test counts samples actually executed via
 * {@link #INVOKE_COUNT} so the abort-before-sampling guarantee can be
 * asserted directly.
 */
public final class SoundnessFloorSubjects {

    public static final String USE_CASE_ID = "soundness-floor-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private SoundnessFloorSubjects() { }

    private static ServiceContract<NoFactors, Integer, Boolean> countingAlwaysPasses() {
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
                .serviceContractFactory(f -> countingAlwaysPasses())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /** VERIFICATION + confidence below the floor → abort, no samples. */
    public static final class VerificationBelowFloor {
        @ProbabilisticTest
        void belowFloor() {
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(0.90, ThresholdOrigin.SLA)
                            .atConfidence(0.50))
                    .assertPasses();
        }
    }

    /**
     * SMOKE + confidence below the floor → abort, no samples. The
     * floor's distinguishing property: it fires under SMOKE too,
     * unlike the (samples, target, confidence) feasibility check.
     */
    public static final class SmokeBelowFloor {
        @ProbabilisticTest
        void belowFloor() {
            PUnit.testing(sampling(50))
                    .criterion(PassRate.<Boolean>meeting(0.90, ThresholdOrigin.SLA)
                            .atConfidence(0.50))
                    .intent(TestIntent.SMOKE)
                    .assertPasses();
        }
    }

    /**
     * VERIFICATION + confidence exactly at the floor (0.80) → no
     * floor abort. The boundary is inclusive at the floor's value.
     */
    public static final class VerificationAtFloorBoundary {
        @ProbabilisticTest
        void atFloor() {
            // Use a permissive target so this passes feasibility too;
            // the test's purpose is to assert the floor doesn't
            // wrongly fire at exactly 0.80.
            PUnit.testing(sampling(100))
                    .criterion(PassRate.<Boolean>meeting(0.50, ThresholdOrigin.SLA)
                            .atConfidence(0.80))
                    .assertPasses();
        }
    }

    /**
     * VERIFICATION at the framework default (0.95) → no floor abort.
     * Sanity check that the floor doesn't wrongly fire on an
     * ordinarily-configured test.
     */
    public static final class VerificationAtFrameworkDefault {
        @ProbabilisticTest
        void atDefault() {
            PUnit.testing(sampling(100))
                    .criterion(PassRate.<Boolean>meeting(0.50, ThresholdOrigin.SLA))
                    .assertPasses();
        }
    }
}
