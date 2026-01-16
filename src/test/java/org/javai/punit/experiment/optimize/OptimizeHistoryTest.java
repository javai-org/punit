package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.junit.jupiter.api.Test;

import static org.javai.punit.model.TerminationReason.*;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OptimizeHistory}.
 */
class OptimizeHistoryTest {

    private OptimizationRecord createIteration(int iterationNumber, double score) {
        FactorSuit factorSuit = FactorSuit.of("systemPrompt", "value" + iterationNumber);
        OptimizeStatistics stats = OptimizeStatistics.fromCounts(
                100, (int) (score * 100), 10000, 100.0
        );
        Instant start = Instant.now().minusSeconds(10);
        OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                iterationNumber, factorSuit, "systemPrompt", stats, start, start.plusSeconds(5)
        );
        return OptimizationRecord.success(aggregate, score);
    }

    @Test
    void shouldBuildMinimalHistory() {
        OptimizeHistory history = OptimizeHistory.builder()
                .useCaseId("shopping")
                .treatmentFactorName("systemPrompt")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now())
                .build();

        assertEquals("shopping", history.useCaseId());
        assertEquals("systemPrompt", history.treatmentFactorName());
        assertEquals(OptimizationObjective.MAXIMIZE, history.objective());
        assertEquals(0, history.iterationCount());
    }

    @Test
    void shouldTrackIterations() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));
        builder.addIteration(createIteration(1, 0.85));
        builder.addIteration(createIteration(2, 0.90));

        OptimizeHistory history = builder.buildPartial();

        assertEquals(3, history.iterationCount());
        assertEquals(3, history.iterations().size());
    }

    @Test
    void shouldFindBestIterationForMaximize() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));
        builder.addIteration(createIteration(1, 0.95));  // Best
        builder.addIteration(createIteration(2, 0.85));

        OptimizeHistory history = builder.buildPartial();

        Optional<OptimizationRecord> best = history.bestIteration();
        assertTrue(best.isPresent());
        assertEquals(1, best.get().iterationNumber());
        assertEquals(0.95, best.get().score());
    }

    @Test
    void shouldFindBestIterationForMinimize() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MINIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));
        builder.addIteration(createIteration(1, 0.50));  // Best (lowest)
        builder.addIteration(createIteration(2, 0.70));

        OptimizeHistory history = builder.buildPartial();

        Optional<OptimizationRecord> best = history.bestIteration();
        assertTrue(best.isPresent());
        assertEquals(1, best.get().iterationNumber());
        assertEquals(0.50, best.get().score());
    }

    @Test
    void shouldReturnEmptyBestWhenNoIterations() {
        OptimizeHistory history = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now())
                .buildPartial();

        assertTrue(history.bestIteration().isEmpty());
        assertTrue(history.bestFactorValue().isEmpty());
        assertTrue(history.bestScore().isEmpty());
    }

    @Test
    void shouldCalculateScoreImprovement() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));  // Initial
        builder.addIteration(createIteration(1, 0.85));
        builder.addIteration(createIteration(2, 0.95));  // Best

        OptimizeHistory history = builder.buildPartial();

        assertEquals(0.15, history.scoreImprovement(), 0.0001);
        assertEquals(18.75, history.scoreImprovementPercent(), 0.01);
    }

    @Test
    void shouldGetLastNIterations() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        for (int i = 0; i < 10; i++) {
            builder.addIteration(createIteration(i, 0.80 + i * 0.01));
        }

        OptimizeHistory history = builder.buildPartial();

        assertEquals(3, history.lastNIterations(3).size());
        assertEquals(7, history.lastNIterations(3).get(0).iterationNumber());
        assertEquals(8, history.lastNIterations(3).get(1).iterationNumber());
        assertEquals(9, history.lastNIterations(3).get(2).iterationNumber());
    }

    @Test
    void shouldGetBestFactorValue() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("systemPrompt")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));
        builder.addIteration(createIteration(1, 0.95));  // Best

        OptimizeHistory history = builder.buildPartial();

        Optional<String> bestValue = history.bestFactorValue();
        assertTrue(bestValue.isPresent());
        assertEquals("value1", bestValue.get());
    }

    @Test
    void shouldCalculateTotalTokens() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        // Each iteration has 10000 tokens
        builder.addIteration(createIteration(0, 0.80));
        builder.addIteration(createIteration(1, 0.85));
        builder.addIteration(createIteration(2, 0.90));

        OptimizeHistory history = builder.buildPartial();

        assertEquals(30000L, history.totalTokens());
    }

    @Test
    void shouldRequireUseCaseId() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void shouldRequireTreatmentFactorName() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void shouldRequireStartTime() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void shouldSupportPartialBuildDuringOptimization() {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("test")
                .treatmentFactorName("factor")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        builder.addIteration(createIteration(0, 0.80));

        // Can build partial without termination reason
        OptimizeHistory partial = builder.buildPartial();
        assertEquals(1, partial.iterationCount());

        // Can continue building
        builder.addIteration(createIteration(1, 0.85));
        OptimizeHistory partial2 = builder.buildPartial();
        assertEquals(2, partial2.iterationCount());
    }

    @Test
    void shouldStoreAllMetadata() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(300);

        OptimizeHistory history = OptimizeHistory.builder()
                .useCaseId("shopping")
                .experimentId("optimize-v1")
                .treatmentFactorName("systemPrompt")
                .treatmentFactorType("String")
                .fixedFactors(FactorSuit.of("model", "gpt-4", "temperature", 0.7))
                .objective(OptimizationObjective.MAXIMIZE)
                .scorerDescription("Success rate scorer")
                .mutatorDescription("LLM mutator")
                .terminationPolicyDescription("Max 20 iterations")
                .startTime(start)
                .endTime(end)
                .terminationReason(OptimizeTerminationReason.maxIterations(20))
                .build();

        assertEquals("shopping", history.useCaseId());
        assertEquals("optimize-v1", history.experimentId());
        assertEquals("systemPrompt", history.treatmentFactorName());
        assertEquals("String", history.treatmentFactorType());
        assertEquals("gpt-4", history.fixedFactors().get("model"));
        assertEquals(0.7, history.fixedFactors().<Double>get("temperature"));
        assertEquals("Success rate scorer", history.scorerDescription());
        assertEquals("LLM mutator", history.mutatorDescription());
        assertEquals("Max 20 iterations", history.terminationPolicyDescription());
        assertEquals(start, history.startTime());
        assertEquals(end, history.endTime());
        assertEquals(MAX_ITERATIONS, history.terminationReason().cause());
    }
}
