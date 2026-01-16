package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.model.UseCaseCriteria;

import static org.javai.punit.model.TerminationReason.*;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OptimizationOrchestrator}.
 */
class OptimizationOrchestratorTest {

    /**
     * Creates a mock use case outcome with the given success rate.
     */
    private UseCaseOutcome createOutcome(boolean success) {
        UseCaseResult result = UseCaseResult.builder()
                .value("response", "test response")
                .value("tokensUsed", 100L)
                .executionTime(Duration.ofMillis(50))
                .build();

        UseCaseCriteria criteria;
        if (success) {
            criteria = UseCaseCriteria.ordered()
                    .criterion("success", () -> true)
                    .build();
        } else {
            criteria = UseCaseCriteria.ordered()
                    .criterion("success", () -> false)
                    .build();
        }

        return new UseCaseOutcome(result, criteria);
    }

    /**
     * Creates a simple executor that returns outcomes with a fixed success rate.
     */
    private UseCaseExecutor createExecutor(double successRate) {
        return (factorSuit, sampleCount) -> {
            List<UseCaseOutcome> outcomes = new ArrayList<>();
            int successCount = (int) (sampleCount * successRate);
            for (int i = 0; i < sampleCount; i++) {
                outcomes.add(createOutcome(i < successCount));
            }
            return outcomes;
        };
    }

    /**
     * Creates an executor that improves over iterations.
     */
    private UseCaseExecutor createImprovingExecutor(AtomicInteger callCount) {
        return (factorSuit, sampleCount) -> {
            int iteration = callCount.getAndIncrement();
            // Success rate improves: 0.7, 0.8, 0.9, 0.95, 0.95...
            double successRate = Math.min(0.95, 0.7 + iteration * 0.1);
            List<UseCaseOutcome> outcomes = new ArrayList<>();
            int successCount = (int) (sampleCount * successRate);
            for (int i = 0; i < sampleCount; i++) {
                outcomes.add(createOutcome(i < successCount));
            }
            return outcomes;
        };
    }

    @Test
    void shouldRunOptimizationLoop() {
        AtomicInteger mutationCount = new AtomicInteger(0);

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test-use-case")
                .experimentId("test-experiment")
                .treatmentFactorName("systemPrompt")
                .treatmentFactorType(String.class)
                .initialFactorValue("Initial prompt")
                .fixedFactors(FactorSuit.of("model", "gpt-4"))
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator((current, history) -> {
                    mutationCount.incrementAndGet();
                    return current + " (improved)";
                })
                .terminationPolicy(new OptimizationMaxIterationsPolicy(5))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8)
        );

        OptimizeHistory history = orchestrator.run();

        assertEquals(5, history.iterationCount());
        assertEquals(MAX_ITERATIONS, history.terminationReason().cause());
        assertEquals(4, mutationCount.get()); // One less than iterations (no mutation after last)
    }

    @Test
    void shouldTerminateOnNoImprovement() {
        AtomicInteger callCount = new AtomicInteger(0);

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator(new NoOpFactorMutator<>())  // No actual change
                .terminationPolicy(new OptimizeCompositeTerminationPolicy(
                        new OptimizationMaxIterationsPolicy(20),
                        new OptimizationNoImprovementPolicy(3)
                ))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8)  // Constant success rate
        );

        OptimizeHistory history = orchestrator.run();

        // Should terminate due to no improvement after 4 iterations (1 initial + 3 no improvement)
        assertTrue(history.iterationCount() <= 20);
        assertEquals(NO_IMPROVEMENT, history.terminationReason().cause());
    }

    @Test
    void shouldFindBestIteration() {
        AtomicInteger callCount = new AtomicInteger(0);

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("v0")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator((current, history) -> "v" + (history.iterationCount()))
                .terminationPolicy(new OptimizationMaxIterationsPolicy(5))
                .samplesPerIteration(20)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createImprovingExecutor(callCount)
        );

        OptimizeHistory history = orchestrator.run();

        assertTrue(history.bestIteration().isPresent());
        // Best should be one of the iterations with highest success rate (0.95)
        // Iterations 3 and 4 both have 0.95, so either could be best
        int bestIterNum = history.bestIteration().get().iterationNumber();
        assertTrue(bestIterNum >= 3, "Best iteration should be 3 or 4, was: " + bestIterNum);
        assertEquals(0.95, history.bestScore().get(), 0.01);
    }

    @Test
    void shouldHandleScoringFailure() {
        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(aggregate -> {
                    throw new ScoringException("Scoring failed");
                })
                .mutator(new NoOpFactorMutator<>())
                .terminationPolicy(new OptimizationMaxIterationsPolicy(10))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8)
        );

        OptimizeHistory history = orchestrator.run();

        assertEquals(1, history.iterationCount());  // Only one attempt
        assertEquals(SCORING_FAILURE, history.terminationReason().cause());
    }

    @Test
    void shouldHandleMutationFailure() {
        AtomicInteger iterationCount = new AtomicInteger(0);

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator((current, history) -> {
                    if (iterationCount.incrementAndGet() >= 3) {
                        throw new MutationException("Mutation failed");
                    }
                    return current + "+";
                })
                .terminationPolicy(new OptimizationMaxIterationsPolicy(10))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8)
        );

        OptimizeHistory history = orchestrator.run();

        assertEquals(3, history.iterationCount());  // 3 successful iterations before failure
        assertEquals(MUTATION_FAILURE, history.terminationReason().cause());
    }

    @Test
    void shouldHandleExecutionFailure() {
        AtomicInteger callCount = new AtomicInteger(0);

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator(new NoOpFactorMutator<>())
                .terminationPolicy(new OptimizationMaxIterationsPolicy(10))
                .samplesPerIteration(10)
                .build();

        UseCaseExecutor failingExecutor = (factorSuit, sampleCount) -> {
            if (callCount.incrementAndGet() >= 3) {
                throw new UseCaseExecutor.ExecutionException("Execution failed");
            }
            return createExecutor(0.8).execute(factorSuit, sampleCount);
        };

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                failingExecutor
        );

        OptimizeHistory history = orchestrator.run();

        assertEquals(3, history.iterationCount());
        assertEquals(SCORING_FAILURE, history.terminationReason().cause());
    }

    @Test
    void shouldInvokeProgressCallback() {
        List<OptimizationRecord> progressRecords = new ArrayList<>();

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator(new NoOpFactorMutator<>())
                .terminationPolicy(new OptimizationMaxIterationsPolicy(3))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8),
                OptimizationOutcomeAggregator.defaultAggregator(),
                progressRecords::add
        );

        orchestrator.run();

        assertEquals(3, progressRecords.size());
        assertEquals(0, progressRecords.get(0).iterationNumber());
        assertEquals(1, progressRecords.get(1).iterationNumber());
        assertEquals(2, progressRecords.get(2).iterationNumber());
    }

    @Test
    void shouldPreserveFixedFactorsAcrossIterations() {
        List<FactorSuit> capturedSuits = new ArrayList<>();

        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .fixedFactors(FactorSuit.of("model", "gpt-4", "temperature", 0.7))
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator((current, history) -> current + "+")
                .terminationPolicy(new OptimizationMaxIterationsPolicy(3))
                .samplesPerIteration(10)
                .build();

        UseCaseExecutor capturingExecutor = (factorSuit, sampleCount) -> {
            capturedSuits.add(factorSuit);
            return createExecutor(0.8).execute(factorSuit, sampleCount);
        };

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                capturingExecutor
        );

        orchestrator.run();

        assertEquals(3, capturedSuits.size());
        for (FactorSuit suit : capturedSuits) {
            assertEquals("gpt-4", suit.get("model"));
            assertEquals(0.7, suit.<Double>get("temperature"));
        }

        // Treatment factor should change
        assertEquals("Initial", capturedSuits.get(0).get("prompt"));
        assertEquals("Initial+", capturedSuits.get(1).get("prompt"));
        assertEquals("Initial++", capturedSuits.get(2).get("prompt"));
    }

    @Test
    void shouldStoreComponentDescriptions() {
        OptimizationConfig<String> config = OptimizationConfig.<String>builder()
                .useCaseId("test")
                .treatmentFactorName("prompt")
                .initialFactorValue("Initial")
                .objective(OptimizationObjective.MAXIMIZE)
                .scorer(new SuccessRateScorer())
                .mutator(new NoOpFactorMutator<>())
                .terminationPolicy(new OptimizeCompositeTerminationPolicy(
                        new OptimizationMaxIterationsPolicy(3),
                        new OptimizationNoImprovementPolicy(2)
                ))
                .samplesPerIteration(10)
                .build();

        OptimizationOrchestrator<String> orchestrator = new OptimizationOrchestrator<>(
                config,
                createExecutor(0.8)
        );

        OptimizeHistory history = orchestrator.run();

        assertTrue(history.scorerDescription().contains("Success rate"));
        assertTrue(history.mutatorDescription().contains("No-op"));
        assertTrue(history.terminationPolicyDescription().contains("OR"));
    }
}
