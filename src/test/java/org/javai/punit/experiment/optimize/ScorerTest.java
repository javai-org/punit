package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Scorer} implementations.
 */
class ScorerTest {

    private static final Instant NOW = Instant.now();

    private OptimizationIterationAggregate createAggregate(double successRate, long totalTokens) {
        FactorSuit factorSuit = FactorSuit.of("treatmentFactor", "value");
        int successCount = (int) Math.round(successRate * 100);
        int failureCount = 100 - successCount;
        OptimizeStatistics stats = new OptimizeStatistics(
                100, successCount, failureCount,
                successRate, totalTokens, 100.0
        );
        return new OptimizationIterationAggregate(
                0, factorSuit, "treatmentFactor", stats, NOW, NOW.plusMillis(1000)
        );
    }

    // === SuccessRateScorer Tests ===

    @Test
    void successRateScorerShouldReturnSuccessRate() throws ScoringException {
        Scorer<OptimizationIterationAggregate> scorer = new SuccessRateScorer();

        assertEquals(0.85, scorer.score(createAggregate(0.85, 10000)));
        assertEquals(0.95, scorer.score(createAggregate(0.95, 10000)));
        assertEquals(0.0, scorer.score(createAggregate(0.0, 10000)));
        assertEquals(1.0, scorer.score(createAggregate(1.0, 10000)));
    }

    @Test
    void successRateScorerShouldHaveDescription() {
        Scorer<OptimizationIterationAggregate> scorer = new SuccessRateScorer();

        assertNotNull(scorer.description());
        assertFalse(scorer.description().isEmpty());
    }

    // === CostEfficiencyScorer Tests ===

    @Test
    void costEfficiencyScorerShouldBalanceSuccessAndTokens() throws ScoringException {
        Scorer<OptimizationIterationAggregate> scorer = new CostEfficiencyScorer();

        // Higher success rate with same tokens = better
        double score1 = scorer.score(createAggregate(0.9, 10000));
        double score2 = scorer.score(createAggregate(0.8, 10000));
        assertTrue(score1 > score2);

        // Same success rate with fewer tokens = better
        double score3 = scorer.score(createAggregate(0.9, 5000));
        double score4 = scorer.score(createAggregate(0.9, 10000));
        assertTrue(score3 > score4);
    }

    @Test
    void costEfficiencyScorerShouldHandleZeroTokens() throws ScoringException {
        Scorer<OptimizationIterationAggregate> scorer = new CostEfficiencyScorer();

        assertEquals(0.0, scorer.score(createAggregate(0.9, 0)));
    }

    @Test
    void costEfficiencyScorerShouldHaveDescription() {
        Scorer<OptimizationIterationAggregate> scorer = new CostEfficiencyScorer();

        assertNotNull(scorer.description());
        assertTrue(scorer.description().contains("efficiency"));
    }

    // === WeightedScorer Tests ===

    @Test
    void weightedScorerShouldCombineScorers() throws ScoringException {
        WeightedScorer scorer = new WeightedScorer(
                new WeightedScorer.WeightedComponent(new SuccessRateScorer(), 0.7),
                new WeightedScorer.WeightedComponent(new CostEfficiencyScorer(), 0.3)
        );

        OptimizationIterationAggregate aggregate = createAggregate(0.9, 10000);

        double score = scorer.score(aggregate);

        // Should be weighted average
        double expectedSuccessComponent = 0.9 * 0.7;
        double expectedEfficiencyComponent = ((0.9 * 1000.0) / 10000) * 0.3;
        double expectedTotal = (expectedSuccessComponent + expectedEfficiencyComponent) / 1.0;

        assertEquals(expectedTotal, score, 0.0001);
    }

    @Test
    void weightedScorerShouldRejectEmptyComponents() {
        assertThrows(IllegalArgumentException.class, () ->
                new WeightedScorer()
        );
    }

    @Test
    void weightedScorerShouldRejectNullScorer() {
        assertThrows(IllegalArgumentException.class, () ->
                new WeightedScorer.WeightedComponent(null, 0.5)
        );
    }

    @Test
    void weightedScorerShouldRejectNegativeWeight() {
        assertThrows(IllegalArgumentException.class, () ->
                new WeightedScorer.WeightedComponent(new SuccessRateScorer(), -0.5)
        );
    }

    @Test
    void weightedScorerShouldHaveDescriptiveDescription() {
        WeightedScorer scorer = new WeightedScorer(
                new WeightedScorer.WeightedComponent(new SuccessRateScorer(), 0.7),
                new WeightedScorer.WeightedComponent(new CostEfficiencyScorer(), 0.3)
        );

        String desc = scorer.description();
        assertTrue(desc.contains("70%"));
        assertTrue(desc.contains("30%"));
    }
}
