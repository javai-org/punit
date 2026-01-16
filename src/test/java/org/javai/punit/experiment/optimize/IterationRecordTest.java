package org.javai.punit.experiment.optimize;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IterationRecord} and {@link IterationAggregate}.
 */
class IterationRecordTest {

    private IterationAggregate createAggregate(int iterationNumber) {
        FactorSuit factorSuit = FactorSuit.of("factor", "value");
        AggregateStatistics stats = AggregateStatistics.fromCounts(100, 80, 10000, 100.0);
        Instant start = Instant.now();
        return new IterationAggregate(
                iterationNumber, factorSuit, "factor", stats, start, start.plusMillis(1000)
        );
    }

    // === IterationRecord Tests ===

    @Test
    void shouldCreateSuccessfulRecord() {
        IterationAggregate aggregate = createAggregate(0);

        IterationRecord record = IterationRecord.success(aggregate, 0.85);

        assertTrue(record.isSuccessful());
        assertEquals(IterationStatus.SUCCESS, record.status());
        assertEquals(0.85, record.score());
        assertEquals(0, record.iterationNumber());
        assertTrue(record.failureReason().isEmpty());
    }

    @Test
    void shouldCreateExecutionFailedRecord() {
        IterationAggregate aggregate = createAggregate(1);

        IterationRecord record = IterationRecord.executionFailed(aggregate, "Connection timeout");

        assertFalse(record.isSuccessful());
        assertEquals(IterationStatus.EXECUTION_FAILED, record.status());
        assertEquals(0.0, record.score());
        assertTrue(record.failureReason().isPresent());
        assertEquals("Connection timeout", record.failureReason().get());
    }

    @Test
    void shouldCreateScoringFailedRecord() {
        IterationAggregate aggregate = createAggregate(2);

        IterationRecord record = IterationRecord.scoringFailed(aggregate, "Invalid data");

        assertFalse(record.isSuccessful());
        assertEquals(IterationStatus.SCORING_FAILED, record.status());
        assertEquals(0.0, record.score());
        assertTrue(record.failureReason().isPresent());
        assertEquals("Invalid data", record.failureReason().get());
    }

    @Test
    void shouldRejectSuccessWithFailureReason() {
        IterationAggregate aggregate = createAggregate(0);

        assertThrows(IllegalArgumentException.class, () ->
                new IterationRecord(aggregate, 0.85, IterationStatus.SUCCESS, Optional.of("reason"))
        );
    }

    @Test
    void shouldRejectFailureWithoutReason() {
        IterationAggregate aggregate = createAggregate(0);

        assertThrows(IllegalArgumentException.class, () ->
                new IterationRecord(aggregate, 0.0, IterationStatus.EXECUTION_FAILED, Optional.empty())
        );
    }

    @Test
    void shouldRejectNullAggregate() {
        assertThrows(IllegalArgumentException.class, () ->
                IterationRecord.success(null, 0.85)
        );
    }

    // === IterationAggregate Tests ===

    @Test
    void shouldCreateAggregate() {
        FactorSuit factorSuit = FactorSuit.of("systemPrompt", "You are helpful");
        AggregateStatistics stats = AggregateStatistics.fromCounts(100, 90, 15000, 150.0);
        Instant start = Instant.now();
        Instant end = start.plusMillis(5000);

        IterationAggregate aggregate = new IterationAggregate(
                3, factorSuit, "systemPrompt", stats, start, end
        );

        assertEquals(3, aggregate.iterationNumber());
        assertEquals(factorSuit, aggregate.factorSuit());
        assertEquals("systemPrompt", aggregate.treatmentFactorName());
        assertEquals(stats, aggregate.statistics());
        assertEquals(start, aggregate.startTime());
        assertEquals(end, aggregate.endTime());
        assertEquals(5000, aggregate.duration().toMillis());
    }

    @Test
    void shouldGetTreatmentFactorValue() {
        FactorSuit factorSuit = FactorSuit.of("systemPrompt", "You are helpful", "model", "gpt-4");
        AggregateStatistics stats = AggregateStatistics.empty();
        Instant start = Instant.now();

        IterationAggregate aggregate = new IterationAggregate(
                0, factorSuit, "systemPrompt", stats, start, start.plusMillis(100)
        );

        assertEquals("You are helpful", aggregate.treatmentFactorValue());
    }

    @Test
    void shouldRejectNegativeIterationNumber() {
        FactorSuit factorSuit = FactorSuit.of("factor", "value");
        AggregateStatistics stats = AggregateStatistics.empty();
        Instant start = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                new IterationAggregate(-1, factorSuit, "factor", stats, start, start.plusMillis(100))
        );
    }

    @Test
    void shouldRejectMissingTreatmentFactor() {
        FactorSuit factorSuit = FactorSuit.of("model", "gpt-4");  // No "systemPrompt"
        AggregateStatistics stats = AggregateStatistics.empty();
        Instant start = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                new IterationAggregate(0, factorSuit, "systemPrompt", stats, start, start.plusMillis(100))
        );
    }

    @Test
    void shouldRejectEndTimeBeforeStartTime() {
        FactorSuit factorSuit = FactorSuit.of("factor", "value");
        AggregateStatistics stats = AggregateStatistics.empty();
        Instant start = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                new IterationAggregate(0, factorSuit, "factor", stats, start, start.minusMillis(100))
        );
    }

    @Test
    void shouldRejectBlankTreatmentFactorName() {
        FactorSuit factorSuit = FactorSuit.of("factor", "value");
        AggregateStatistics stats = AggregateStatistics.empty();
        Instant start = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                new IterationAggregate(0, factorSuit, "", stats, start, start.plusMillis(100))
        );

        assertThrows(IllegalArgumentException.class, () ->
                new IterationAggregate(0, factorSuit, "   ", stats, start, start.plusMillis(100))
        );
    }
}
