package org.javai.punit.experiment.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AggregateStatistics}.
 */
class AggregateStatisticsTest {

    @Test
    void shouldCreateFromExplicitValues() {
        AggregateStatistics stats = new AggregateStatistics(
                100, 85, 15, 0.85, 50000L, 120.5
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
        AggregateStatistics stats = AggregateStatistics.fromCounts(
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
        AggregateStatistics stats = AggregateStatistics.empty();

        assertEquals(0, stats.sampleCount());
        assertEquals(0, stats.successCount());
        assertEquals(0, stats.failureCount());
        assertEquals(0.0, stats.successRate());
        assertEquals(0L, stats.totalTokens());
        assertEquals(0.0, stats.meanLatencyMs());
    }

    @Test
    void shouldHandleZeroSamplesInFromCounts() {
        AggregateStatistics stats = AggregateStatistics.fromCounts(0, 0, 0L, 0.0);

        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.successRate());
    }

    @Test
    void shouldRejectNegativeSampleCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(-1, 0, 0, 0.0, 0L, 0.0)
        );
    }

    @Test
    void shouldRejectNegativeSuccessCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, -1, 11, 0.0, 0L, 0.0)
        );
    }

    @Test
    void shouldRejectMismatchedCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, 5, 6, 0.5, 0L, 0.0)  // 5 + 6 != 10
        );
    }

    @Test
    void shouldRejectInvalidSuccessRate() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, 5, 5, 1.5, 0L, 0.0)  // > 1.0
        );

        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, 5, 5, -0.1, 0L, 0.0)  // < 0.0
        );
    }

    @Test
    void shouldRejectNegativeTokens() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, 5, 5, 0.5, -1L, 0.0)
        );
    }

    @Test
    void shouldRejectNegativeLatency() {
        assertThrows(IllegalArgumentException.class, () ->
                new AggregateStatistics(10, 5, 5, 0.5, 0L, -1.0)
        );
    }
}
