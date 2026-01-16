package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for pacing in experiment execution.
 *
 * <p>These tests verify that the {@link Pacing} annotation works correctly
 * with {@link Experiment} methods in both MEASURE and EXPLORE modes.
 */
class ExperimentPacingIntegrationTest {

    @Nested
    @DisplayName("MEASURE mode pacing")
    class MeasureModePacing {

        @Test
        @DisplayName("MEASURE experiment with pacing applies delays between samples")
        void measureWithPacing_appliesDelays() {
            PacedMeasureExperiment.reset();

            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectMethod(
                            PacedMeasureExperiment.class, "experimentWithPacing",
                            ResultCaptor.class))
                    .execute();

            // Verify all samples executed
            results.testEvents().succeeded().assertThatEvents().hasSize(5);

            // Verify timing: with 100ms delay between samples, 5 samples should take at least 400ms
            // (delay is applied between samples, not before first)
            long totalTime = PacedMeasureExperiment.totalExecutionTime.get();
            assertThat(totalTime).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("MEASURE experiment without pacing executes without delays")
        void measureWithoutPacing_noDelays() {
            UnpacedMeasureExperiment.reset();

            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectMethod(
                            UnpacedMeasureExperiment.class, "experimentWithoutPacing",
                            ResultCaptor.class))
                    .execute();

            // Verify all samples executed
            results.testEvents().succeeded().assertThatEvents().hasSize(5);

            // Verify timing: without pacing, should complete quickly
            long totalTime = UnpacedMeasureExperiment.totalExecutionTime.get();
            // Should be much less than if we had 100ms delays (which would add 400ms)
            assertThat(totalTime).isLessThan(400);
        }
    }

    @Nested
    @DisplayName("EXPLORE mode pacing")
    class ExploreModePacing {

        @Test
        @DisplayName("EXPLORE experiment applies pacing continuously across configurations")
        void exploreWithPacing_continuousPacingAcrossConfigs() {
            PacedExploreExperiment.reset();

            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectMethod(
                            PacedExploreExperiment.class, "experimentWithPacing",
                            String.class, ResultCaptor.class))
                    .execute();

            // 2 factor values × 3 samples per config = 6 total samples
            results.testEvents().succeeded().assertThatEvents().hasSize(6);

            // Verify timing: with 100ms delay between samples, 6 samples should take at least 500ms
            // (delay is applied between samples, not before first - so 5 delays)
            long totalTime = PacedExploreExperiment.totalExecutionTime.get();
            assertThat(totalTime).isGreaterThanOrEqualTo(500);
        }

        @Test
        @DisplayName("EXPLORE mode uses global counter (no reset between configs)")
        void exploreWithPacing_globalCounterAcrossConfigs() {
            GlobalCounterExploreExperiment.reset();

            EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectMethod(
                            GlobalCounterExploreExperiment.class, "experimentWithGlobalCounter",
                            String.class, ResultCaptor.class))
                    .execute();

            // 2 configs × 2 samples = 4 samples total
            // With global counter: delays happen at samples 2, 3, 4 (3 delays)
            // If counter reset per config: delays would only happen at sample 2 of each config (2 delays)
            // With 150ms delay, global counter should show ~450ms, reset would show ~300ms
            long totalTime = GlobalCounterExploreExperiment.totalExecutionTime.get();
            assertThat(totalTime).isGreaterThanOrEqualTo(400); // Expect ~450ms (3 × 150ms)
        }
    }

    @Nested
    @DisplayName("Pacing timing verification")
    class PacingTimingVerification {

        @Test
        @DisplayName("Pacing delay is applied correctly between samples")
        void pacingDelay_appliedBetweenSamples() {
            TimingExperiment.reset();

            EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectMethod(
                            TimingExperiment.class, "experimentWithTimingCheck",
                            ResultCaptor.class))
                    .execute();

            // Verify samples executed in order with proper delays
            assertThat(TimingExperiment.sampleCount.get()).isEqualTo(3);

            // First sample should have no delay, subsequent samples should have delays
            // The minimum inter-sample time should be close to 150ms
            long avgInterSampleTime = TimingExperiment.totalInterSampleTime.get() / 2; // 2 gaps between 3 samples
            assertThat(avgInterSampleTime).isGreaterThanOrEqualTo(140); // Allow some tolerance
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST EXPERIMENT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * MEASURE experiment with pacing.
     */
    public static class PacedMeasureExperiment {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @Experiment(mode = ExperimentMode.MEASURE, samples = 5, expiresInDays = 0)
        @Pacing(minMsPerSample = 100)
        public void experimentWithPacing(ResultCaptor captor) {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 5) {
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            captor.record(UseCaseResult.builder().value("sample", count).build());
        }
    }

    /**
     * MEASURE experiment without pacing.
     */
    public static class UnpacedMeasureExperiment {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @Experiment(mode = ExperimentMode.MEASURE, samples = 5, expiresInDays = 0)
        public void experimentWithoutPacing(ResultCaptor captor) {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 5) {
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            captor.record(UseCaseResult.builder().value("sample", count).build());
        }
    }

    /**
     * EXPLORE experiment with pacing across configs.
     */
    public static class PacedExploreExperiment {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @Experiment(mode = ExperimentMode.EXPLORE, samplesPerConfig = 3, expiresInDays = 0)
        @FactorSource("options")
        @Pacing(minMsPerSample = 100)
        public void experimentWithPacing(
                @Factor("option") String option,
                ResultCaptor captor) {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 6) { // 2 configs × 3 samples = 6
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            captor.record(UseCaseResult.builder().value("option", option).value("sample", count).build());
        }

        static Stream<FactorArguments> options() {
            return FactorArguments.configurations()
                    .names("option")
                    .values("option-a")
                    .values("option-b")
                    .stream();
        }
    }

    /**
     * EXPLORE experiment that verifies global sample counter.
     */
    public static class GlobalCounterExploreExperiment {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @Experiment(mode = ExperimentMode.EXPLORE, samplesPerConfig = 2, expiresInDays = 0)
        @FactorSource("configs")
        @Pacing(minMsPerSample = 150)
        public void experimentWithGlobalCounter(
                @Factor("config") String config,
                ResultCaptor captor) {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 4) { // 2 configs × 2 samples = 4
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            captor.record(UseCaseResult.builder().value("config", config).value("sample", count).build());
        }

        static Stream<FactorArguments> configs() {
            return FactorArguments.configurations()
                    .names("config")
                    .values("config-1")
                    .values("config-2")
                    .stream();
        }
    }

    /**
     * Experiment for timing verification.
     */
    public static class TimingExperiment {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong lastSampleTime = new AtomicLong(0);
        static final AtomicLong totalInterSampleTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            lastSampleTime.set(0);
            totalInterSampleTime.set(0);
        }

        @Experiment(mode = ExperimentMode.MEASURE, samples = 3, expiresInDays = 0)
        @Pacing(minMsPerSample = 150)
        public void experimentWithTimingCheck(ResultCaptor captor) {
            long now = System.currentTimeMillis();
            int count = sampleCount.incrementAndGet();

            if (count > 1) {
                long interSampleTime = now - lastSampleTime.get();
                totalInterSampleTime.addAndGet(interSampleTime);
            }

            lastSampleTime.set(now);
            captor.record(UseCaseResult.builder().value("sample", count).build());
        }
    }
}
