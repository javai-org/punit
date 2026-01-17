package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.ProbabilisticTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for pacing in probabilistic test execution.
 */
class PacingIntegrationTest {

    @Test
    @DisplayName("Test with pacing executes with delays between samples")
    void testWithPacing_appliesDelays() {
        // Reset counters
        PacedTestClass.reset();

        EngineExecutionResults results = EngineTestKit
                .engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectMethod(PacedTestClass.class, "testWithPacing"))
                .execute();

        // Verify all samples executed
        results.testEvents().succeeded().assertThatEvents().hasSize(5);

        // Verify timing: with 100ms delay between samples, 5 samples should take at least 400ms
        // (delay is applied between samples, not before first)
        long totalTime = PacedTestClass.totalExecutionTime.get();
        assertThat(totalTime).isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("Test without pacing executes without delays")
    void testWithoutPacing_noDelays() {
        // Reset counters
        UnpacedTestClass.reset();

        EngineExecutionResults results = EngineTestKit
                .engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectMethod(UnpacedTestClass.class, "testWithoutPacing"))
                .execute();

        // Verify all samples executed
        results.testEvents().succeeded().assertThatEvents().hasSize(5);

        // Verify timing: without pacing, should complete quickly
        long totalTime = UnpacedTestClass.totalExecutionTime.get();
        // Should be much less than if we had 100ms delays (which would add 400ms)
        assertThat(totalTime).isLessThan(400);
    }

    @Test
    @DisplayName("Pacing delay is applied correctly between samples")
    void pacingDelay_appliedBetweenSamples() {
        // Reset counters
        TimingTestClass.reset();

        EngineTestKit
                .engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectMethod(TimingTestClass.class, "testWithTimingCheck"))
                .execute();

        // Verify samples executed in order with proper delays
        assertThat(TimingTestClass.sampleCount.get()).isEqualTo(3);
        
        // First sample should have no delay, subsequent samples should have delays
        // The minimum inter-sample time should be close to 150ms
        long avgInterSampleTime = TimingTestClass.totalInterSampleTime.get() / 2; // 2 gaps between 3 samples
        assertThat(avgInterSampleTime).isGreaterThanOrEqualTo(140); // Allow some tolerance
    }

    // Test class with pacing
    public static class PacedTestClass {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @ProbabilisticTest(samples = 5, minPassRate = 1.0)
        @Pacing(minMsPerSample = 100)
        void testWithPacing() {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 5) {
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            // Always pass
            assertThat(true).isTrue();
        }
    }

    // Test class without pacing
    public static class UnpacedTestClass {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong startTime = new AtomicLong(0);
        static final AtomicLong totalExecutionTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            startTime.set(0);
            totalExecutionTime.set(0);
        }

        @ProbabilisticTest(samples = 5, minPassRate = 1.0)
        void testWithoutPacing() {
            int count = sampleCount.incrementAndGet();
            if (count == 1) {
                startTime.set(System.currentTimeMillis());
            } else if (count == 5) {
                totalExecutionTime.set(System.currentTimeMillis() - startTime.get());
            }
            // Always pass
            assertThat(true).isTrue();
        }
    }

    // Test class for timing verification
    public static class TimingTestClass {
        static final AtomicInteger sampleCount = new AtomicInteger(0);
        static final AtomicLong lastSampleTime = new AtomicLong(0);
        static final AtomicLong totalInterSampleTime = new AtomicLong(0);

        static void reset() {
            sampleCount.set(0);
            lastSampleTime.set(0);
            totalInterSampleTime.set(0);
        }

        @ProbabilisticTest(samples = 3, minPassRate = 1.0)
        @Pacing(minMsPerSample = 150)
        void testWithTimingCheck() {
            long now = System.currentTimeMillis();
            int count = sampleCount.incrementAndGet();
            
            if (count > 1) {
                long interSampleTime = now - lastSampleTime.get();
                totalInterSampleTime.addAndGet(interSampleTime);
            }
            
            lastSampleTime.set(now);
            
            // Always pass
            assertThat(true).isTrue();
        }
    }
}

