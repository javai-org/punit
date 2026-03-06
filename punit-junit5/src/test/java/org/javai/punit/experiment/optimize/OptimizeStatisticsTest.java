package org.javai.punit.experiment.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.javai.punit.experiment.model.EmpiricalSummary;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OptimizeStatistics}.
 */
class OptimizeStatisticsTest {

    @Test
    void shouldCreateFromExplicitValues() {
        OptimizeStatistics stats = new OptimizeStatistics(
                100, 85, 15, 0.85, 50000L, 120.5, null
        );

        assertEquals(100, stats.sampleCount());
        assertEquals(85, stats.successCount());
        assertEquals(15, stats.failureCount());
        assertEquals(0.85, stats.successRate());
        assertEquals(50000L, stats.totalTokens());
        assertEquals(120.5, stats.meanLatencyMs());
    }

    @Test
    void shouldCreateFromCounts() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(
                100, 85, 50000L, 120.5
        );

        assertEquals(100, stats.sampleCount());
        assertEquals(85, stats.successCount());
        assertEquals(15, stats.failureCount());
        assertEquals(0.85, stats.successRate(), 0.0001);
        assertEquals(50000L, stats.totalTokens());
        assertEquals(120.5, stats.meanLatencyMs());
    }

    @Test
    void shouldCreateEmpty() {
        OptimizeStatistics stats = OptimizeStatistics.empty();

        assertEquals(0, stats.sampleCount());
        assertEquals(0, stats.successCount());
        assertEquals(0, stats.failureCount());
        assertEquals(0.0, stats.successRate());
        assertEquals(0L, stats.totalTokens());
        assertEquals(0.0, stats.meanLatencyMs());
    }

    @Test
    void shouldHandleZeroSamplesInFromCounts() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(0, 0, 0L, 0.0);

        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.successRate());
    }

    @Test
    void shouldRejectNegativeSampleCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(-1, 0, 0, 0.0, 0L, 0.0, null)
        );
    }

    @Test
    void shouldRejectNegativeSuccessCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, -1, 11, 0.0, 0L, 0.0, null)
        );
    }

    @Test
    void shouldRejectMismatchedCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, 5, 6, 0.5, 0L, 0.0, null)  // 5 + 6 != 10
        );
    }

    @Test
    void shouldRejectInvalidSuccessRate() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, 5, 5, 1.5, 0L, 0.0, null)  // > 1.0
        );

        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, 5, 5, -0.1, 0L, 0.0, null)  // < 0.0
        );
    }

    @Test
    void shouldRejectNegativeTokens() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, 5, 5, 0.5, -1L, 0.0, null)
        );
    }

    @Test
    void shouldRejectNegativeLatency() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeStatistics(10, 5, 5, 0.5, 0L, -1.0, null)
        );
    }

    // === EmpiricalSummary Implementation Tests ===

    @Test
    void shouldImplementSuccesses() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertEquals(85, stats.successes());
    }

    @Test
    void shouldImplementFailures() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertEquals(15, stats.failures());
    }

    @Test
    void shouldImplementSamplesExecuted() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertEquals(100, stats.samplesExecuted());
    }

    @Test
    void shouldImplementAvgLatencyMs() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertEquals(121L, stats.avgLatencyMs());  // Rounded from 120.5
    }

    @Test
    void shouldImplementAvgTokensPerSample() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertEquals(500L, stats.avgTokensPerSample());  // 50000 / 100
    }

    @Test
    void shouldImplementAvgTokensPerSampleWithZeroSamples() {
        OptimizeStatistics stats = OptimizeStatistics.empty();

        assertEquals(0L, stats.avgTokensPerSample());
    }

    @Test
    void shouldImplementFailureDistribution() {
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 85, 50000L, 120.5);

        assertTrue(stats.failureDistribution().isEmpty());
    }

    @Test
    void shouldBeUsableAsEmpiricalSummary() {
        EmpiricalSummary summary = OptimizeStatistics.fromCounts(100, 80, 20000L, 50.0);

        assertEquals(80, summary.successes());
        assertEquals(20, summary.failures());
        assertEquals(100, summary.samplesExecuted());
        assertEquals(50L, summary.avgLatencyMs());
        assertEquals(200L, summary.avgTokensPerSample());
    }
}
