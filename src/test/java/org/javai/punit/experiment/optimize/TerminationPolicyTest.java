package org.javai.punit.experiment.optimize;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TerminationPolicy} implementations.
 */
class TerminationPolicyTest {

    // === Helper Methods ===

    private OptimizationHistory createHistory(int iterationCount, int bestIteration) {
        OptimizationHistory.Builder builder = OptimizationHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now().minusSeconds(iterationCount * 10));

        for (int i = 0; i < iterationCount; i++) {
            double score = (i == bestIteration) ? 0.95 : 0.80;
            builder.addIteration(createIteration(i, score));
        }

        return builder.buildPartial();
    }

    private OptimizationHistory createHistoryWithDuration(Duration duration) {
        return OptimizationHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now().minus(duration))
                .endTime(Instant.now())
                .buildPartial();
    }

    private IterationRecord createIteration(int iterationNumber, double score) {
        FactorSuit factorSuit = FactorSuit.of("factor", "value" + iterationNumber);
        AggregateStatistics stats = AggregateStatistics.fromCounts(100, 80, 10000, 100.0);
        Instant start = Instant.now().minusSeconds(10);
        IterationAggregate aggregate = new IterationAggregate(
                iterationNumber, factorSuit, "factor", stats, start, start.plusSeconds(5)
        );
        return IterationRecord.success(aggregate, score);
    }

    // === MaxIterationsPolicy Tests ===

    @Test
    void maxIterationsPolicyShouldTerminateAtMax() {
        TerminationPolicy policy = new MaxIterationsPolicy(10);

        // Should not terminate before max
        OptimizationHistory history9 = createHistory(9, 0);
        assertTrue(policy.shouldTerminate(history9).isEmpty());

        // Should terminate at max
        OptimizationHistory history10 = createHistory(10, 0);
        Optional<TerminationReason> reason = policy.shouldTerminate(history10);
        assertTrue(reason.isPresent());
        assertEquals(TerminationCause.MAX_ITERATIONS, reason.get().cause());
    }

    @Test
    void maxIterationsPolicyShouldRejectZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaxIterationsPolicy(0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MaxIterationsPolicy(-1)
        );
    }

    @Test
    void maxIterationsPolicyShouldHaveDescription() {
        TerminationPolicy policy = new MaxIterationsPolicy(20);

        assertTrue(policy.description().contains("20"));
    }

    // === NoImprovementPolicy Tests ===

    @Test
    void noImprovementPolicyShouldTerminateWhenNoImprovement() {
        TerminationPolicy policy = new NoImprovementPolicy(3);

        // Best at iteration 2, currently at iteration 6 (4 iterations since best)
        // Iterations: 0, 1, 2(best), 3, 4, 5, 6
        // iterationsSinceBest = 6 - 2 = 4, windowSize = 3, 4 >= 3 -> terminate
        OptimizationHistory history = createHistory(7, 2);

        Optional<TerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(TerminationCause.NO_IMPROVEMENT, reason.get().cause());
    }

    @Test
    void noImprovementPolicyShouldNotTerminateWithRecentImprovement() {
        TerminationPolicy policy = new NoImprovementPolicy(3);

        // Best at iteration 5, currently at iteration 6
        // iterationsSinceBest = 6 - 5 = 1, windowSize = 3, 1 < 3 -> don't terminate
        OptimizationHistory history = createHistory(7, 5);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void noImprovementPolicyShouldNotTerminateWithInsufficientHistory() {
        TerminationPolicy policy = new NoImprovementPolicy(5);

        // Only 3 iterations, window is 5
        OptimizationHistory history = createHistory(3, 0);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void noImprovementPolicyShouldRejectInvalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () ->
                new NoImprovementPolicy(0)
        );
    }

    // === TimeBudgetPolicy Tests ===

    @Test
    void timeBudgetPolicyShouldTerminateWhenExceeded() {
        TerminationPolicy policy = new TimeBudgetPolicy(Duration.ofMinutes(5));

        OptimizationHistory history = createHistoryWithDuration(Duration.ofMinutes(6));

        Optional<TerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(TerminationCause.TIME_BUDGET_EXHAUSTED, reason.get().cause());
    }

    @Test
    void timeBudgetPolicyShouldNotTerminateWithinBudget() {
        TerminationPolicy policy = new TimeBudgetPolicy(Duration.ofMinutes(5));

        OptimizationHistory history = createHistoryWithDuration(Duration.ofMinutes(3));

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void timeBudgetPolicyShouldRejectInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimeBudgetPolicy(null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new TimeBudgetPolicy(Duration.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new TimeBudgetPolicy(Duration.ofMinutes(-1))
        );
    }

    @Test
    void timeBudgetPolicyShouldCreateFromMillis() {
        TerminationPolicy policy = TimeBudgetPolicy.ofMillis(300000);

        assertTrue(policy.description().contains("5m"));
    }

    // === CompositeTerminationPolicy Tests ===

    @Test
    void compositePolicyShouldTerminateWhenAnyPolicyTriggers() {
        TerminationPolicy policy = new CompositeTerminationPolicy(
                new MaxIterationsPolicy(100),  // Won't trigger
                new NoImprovementPolicy(2)      // Will trigger
        );

        // Best at iteration 0, 3 iterations since
        OptimizationHistory history = createHistory(4, 0);

        Optional<TerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(TerminationCause.NO_IMPROVEMENT, reason.get().cause());
    }

    @Test
    void compositePolicyShouldNotTerminateWhenNoPolicyTriggers() {
        TerminationPolicy policy = new CompositeTerminationPolicy(
                new MaxIterationsPolicy(100),
                new NoImprovementPolicy(10)
        );

        OptimizationHistory history = createHistory(5, 4);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void compositePolicyShouldRejectEmptyPolicies() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompositeTerminationPolicy()
        );
    }

    @Test
    void compositePolicyShouldHaveCombinedDescription() {
        TerminationPolicy policy = new CompositeTerminationPolicy(
                new MaxIterationsPolicy(20),
                new NoImprovementPolicy(5)
        );

        String desc = policy.description();
        assertTrue(desc.contains("OR"));
        assertTrue(desc.contains("20"));
        assertTrue(desc.contains("5"));
    }
}
