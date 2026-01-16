package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.junit.jupiter.api.Test;

import static org.javai.punit.model.TerminationReason.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OptimizeTerminationPolicy} implementations.
 */
class OptimizeTerminationPolicyTest {

    // === Helper Methods ===

    private OptimizeHistory createHistory(int iterationCount, int bestIteration) {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
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

    private OptimizeHistory createHistoryWithDuration(Duration duration) {
        return OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now().minus(duration))
                .endTime(Instant.now())
                .buildPartial();
    }

    private OptimizationRecord createIteration(int iterationNumber, double score) {
        FactorSuit factorSuit = FactorSuit.of("factor", "value" + iterationNumber);
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(100, 80, 10000, 100.0);
        Instant start = Instant.now().minusSeconds(10);
        OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                iterationNumber, factorSuit, "factor", stats, start, start.plusSeconds(5)
        );
        return OptimizationRecord.success(aggregate, score);
    }

    // === OptimizationMaxIterationsPolicy Tests ===

    @Test
    void maxIterationsPolicyShouldTerminateAtMax() {
        OptimizeTerminationPolicy policy = new OptimizationMaxIterationsPolicy(10);

        // Should not terminate before max
        OptimizeHistory history9 = createHistory(9, 0);
        assertTrue(policy.shouldTerminate(history9).isEmpty());

        // Should terminate at max
        OptimizeHistory history10 = createHistory(10, 0);
        Optional<OptimizeTerminationReason> reason = policy.shouldTerminate(history10);
        assertTrue(reason.isPresent());
        assertEquals(MAX_ITERATIONS, reason.get().cause());
    }

    @Test
    void maxIterationsPolicyShouldRejectZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizationMaxIterationsPolicy(0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizationMaxIterationsPolicy(-1)
        );
    }

    @Test
    void maxIterationsPolicyShouldHaveDescription() {
        OptimizeTerminationPolicy policy = new OptimizationMaxIterationsPolicy(20);

        assertTrue(policy.description().contains("20"));
    }

    // === OptimizationNoImprovementPolicy Tests ===

    @Test
    void noImprovementPolicyShouldTerminateWhenNoImprovement() {
        OptimizeTerminationPolicy policy = new OptimizationNoImprovementPolicy(3);

        // Best at iteration 2, currently at iteration 6 (4 iterations since best)
        // Iterations: 0, 1, 2(best), 3, 4, 5, 6
        // iterationsSinceBest = 6 - 2 = 4, windowSize = 3, 4 >= 3 -> terminate
        OptimizeHistory history = createHistory(7, 2);

        Optional<OptimizeTerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(NO_IMPROVEMENT, reason.get().cause());
    }

    @Test
    void noImprovementPolicyShouldNotTerminateWithRecentImprovement() {
        OptimizeTerminationPolicy policy = new OptimizationNoImprovementPolicy(3);

        // Best at iteration 5, currently at iteration 6
        // iterationsSinceBest = 6 - 5 = 1, windowSize = 3, 1 < 3 -> don't terminate
        OptimizeHistory history = createHistory(7, 5);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void noImprovementPolicyShouldNotTerminateWithInsufficientHistory() {
        OptimizeTerminationPolicy policy = new OptimizationNoImprovementPolicy(5);

        // Only 3 iterations, window is 5
        OptimizeHistory history = createHistory(3, 0);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void noImprovementPolicyShouldRejectInvalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizationNoImprovementPolicy(0)
        );
    }

    // === OptimizeTimeBudgetPolicy Tests ===

    @Test
    void timeBudgetPolicyShouldTerminateWhenExceeded() {
        OptimizeTerminationPolicy policy = new OptimizeTimeBudgetPolicy(Duration.ofMinutes(5));

        OptimizeHistory history = createHistoryWithDuration(Duration.ofMinutes(6));

        Optional<OptimizeTerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(OPTIMIZATION_TIME_BUDGET_EXHAUSTED, reason.get().cause());
    }

    @Test
    void timeBudgetPolicyShouldNotTerminateWithinBudget() {
        OptimizeTerminationPolicy policy = new OptimizeTimeBudgetPolicy(Duration.ofMinutes(5));

        OptimizeHistory history = createHistoryWithDuration(Duration.ofMinutes(3));

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void timeBudgetPolicyShouldRejectInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeTimeBudgetPolicy(null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeTimeBudgetPolicy(Duration.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeTimeBudgetPolicy(Duration.ofMinutes(-1))
        );
    }

    @Test
    void timeBudgetPolicyShouldCreateFromMillis() {
        OptimizeTerminationPolicy policy = OptimizeTimeBudgetPolicy.ofMillis(300000);

        assertTrue(policy.description().contains("5m"));
    }

    // === OptimizeCompositeTerminationPolicy Tests ===

    @Test
    void compositePolicyShouldTerminateWhenAnyPolicyTriggers() {
        OptimizeTerminationPolicy policy = new OptimizeCompositeTerminationPolicy(
                new OptimizationMaxIterationsPolicy(100),  // Won't trigger
                new OptimizationNoImprovementPolicy(2)      // Will trigger
        );

        // Best at iteration 0, 3 iterations since
        OptimizeHistory history = createHistory(4, 0);

        Optional<OptimizeTerminationReason> reason = policy.shouldTerminate(history);
        assertTrue(reason.isPresent());
        assertEquals(NO_IMPROVEMENT, reason.get().cause());
    }

    @Test
    void compositePolicyShouldNotTerminateWhenNoPolicyTriggers() {
        OptimizeTerminationPolicy policy = new OptimizeCompositeTerminationPolicy(
                new OptimizationMaxIterationsPolicy(100),
                new OptimizationNoImprovementPolicy(10)
        );

        OptimizeHistory history = createHistory(5, 4);

        assertTrue(policy.shouldTerminate(history).isEmpty());
    }

    @Test
    void compositePolicyShouldRejectEmptyPolicies() {
        assertThrows(IllegalArgumentException.class, () ->
                new OptimizeCompositeTerminationPolicy()
        );
    }

    @Test
    void compositePolicyShouldHaveCombinedDescription() {
        OptimizeTerminationPolicy policy = new OptimizeCompositeTerminationPolicy(
                new OptimizationMaxIterationsPolicy(20),
                new OptimizationNoImprovementPolicy(5)
        );

        String desc = policy.description();
        assertTrue(desc.contains("OR"));
        assertTrue(desc.contains("20"));
        assertTrue(desc.contains("5"));
    }
}
