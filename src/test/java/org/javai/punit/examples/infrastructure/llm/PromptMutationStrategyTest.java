package org.javai.punit.examples.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizationIterationAggregate;
import org.javai.punit.experiment.optimize.OptimizationObjective;
import org.javai.punit.experiment.optimize.OptimizationRecord;
import org.javai.punit.experiment.optimize.OptimizeHistory;
import org.javai.punit.experiment.optimize.OptimizeStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Prompt Mutation Strategies")
class PromptMutationStrategyTest {

    @Nested
    @DisplayName("DeterministicPromptMutationStrategy")
    class DeterministicStrategyTest {

        private final DeterministicPromptMutationStrategy strategy = new DeterministicPromptMutationStrategy();

        @Test
        @DisplayName("returns first progression prompt after iteration 0")
        void returnsFirstProgressionPromptAfterIteration0() throws MutationException {
            OptimizeHistory history = buildHistoryWithIterations(1);

            String result = strategy.mutate("initial prompt", history);

            assertThat(result).contains("ONLY valid JSON");
        }

        @Test
        @DisplayName("returns second progression prompt after iteration 1")
        void returnsSecondProgressionPromptAfterIteration1() throws MutationException {
            OptimizeHistory history = buildHistoryWithIterations(2);

            String result = strategy.mutate("initial prompt", history);

            assertThat(result).contains("operations");
            assertThat(result).contains("array");
        }

        @Test
        @DisplayName("returns final prompt when beyond progression length")
        void returnsFinalPromptWhenBeyondProgressionLength() throws MutationException {
            OptimizeHistory history = buildHistoryWithIterations(10);

            String result = strategy.mutate("initial prompt", history);

            assertThat(result).contains("STRICT RULES");
            assertThat(result).contains("Example");
        }

        @Test
        @DisplayName("description identifies as deterministic")
        void descriptionIdentifiesAsDeterministic() {
            assertThat(strategy.description()).containsIgnoringCase("deterministic");
        }
    }

    @Nested
    @DisplayName("LlmPromptMutationStrategy")
    class LlmStrategyTest {

        @Test
        @DisplayName("resolveMutationModel returns default when not configured")
        void resolveMutationModelReturnsDefaultWhenNotConfigured() {
            // Clear any existing configuration
            System.clearProperty("punit.llm.mutation.model");

            String model = LlmPromptMutationStrategy.resolveMutationModel();

            assertThat(model).isEqualTo("gpt-4o-mini");
        }

        @Test
        @DisplayName("resolveMutationModel returns system property when set")
        void resolveMutationModelReturnsSystemPropertyWhenSet() {
            try {
                System.setProperty("punit.llm.mutation.model", "gpt-4o");

                String model = LlmPromptMutationStrategy.resolveMutationModel();

                assertThat(model).isEqualTo("gpt-4o");
            } finally {
                System.clearProperty("punit.llm.mutation.model");
            }
        }

        @Test
        @DisplayName("description includes model name")
        void descriptionIncludesModelName() {
            LlmPromptMutationStrategy strategy = new LlmPromptMutationStrategy(MockChatLlm.instance());

            assertThat(strategy.description()).contains("gpt-4o-mini");
        }

        @Test
        @DisplayName("uses mock LLM to generate mutation in mock mode")
        void usesMockLlmToGenerateMutationInMockMode() throws MutationException {
            LlmPromptMutationStrategy strategy = new LlmPromptMutationStrategy(MockChatLlm.instance());
            OptimizeHistory history = buildHistoryWithIterations(1);

            String result = strategy.mutate("initial prompt", history);

            // MockChatLlm returns valid JSON, so we should get something back
            assertThat(result).isNotBlank();
        }
    }

    @Nested
    @DisplayName("ShoppingBasketPromptMutator integration")
    class MutatorIntegrationTest {

        @Test
        @DisplayName("uses deterministic strategy in mock mode")
        void usesDeterministicStrategyInMockMode() {
            // Ensure mock mode
            System.clearProperty("punit.llm.mode");

            org.javai.punit.examples.experiments.ShoppingBasketPromptMutator mutator =
                    new org.javai.punit.examples.experiments.ShoppingBasketPromptMutator();

            assertThat(mutator.description()).containsIgnoringCase("deterministic");
        }

        @Test
        @DisplayName("validate rejects null prompt")
        void validateRejectsNullPrompt() {
            org.javai.punit.examples.experiments.ShoppingBasketPromptMutator mutator =
                    new org.javai.punit.examples.experiments.ShoppingBasketPromptMutator();

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutator.validate(null))
                    .isInstanceOf(MutationException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("validate rejects overly long prompt")
        void validateRejectsOverlyLongPrompt() {
            org.javai.punit.examples.experiments.ShoppingBasketPromptMutator mutator =
                    new org.javai.punit.examples.experiments.ShoppingBasketPromptMutator();
            String longPrompt = "x".repeat(10001);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutator.validate(longPrompt))
                    .isInstanceOf(MutationException.class)
                    .hasMessageContaining("10000");
        }
    }

    // Helper methods

    private OptimizeHistory buildHistoryWithIterations(int count) {
        OptimizeHistory.Builder builder = OptimizeHistory.builder()
                .useCaseId("TestUseCase")
                .experimentId("test-experiment")
                .controlFactorName("systemPrompt")
                .controlFactorType("String")
                .objective(OptimizationObjective.MAXIMIZE)
                .startTime(Instant.now());

        for (int i = 0; i < count; i++) {
            FactorSuit factorSuit = FactorSuit.of("systemPrompt", "prompt " + i);

            OptimizeStatistics stats = OptimizeStatistics.fromCounts(20, 15, 100, 50.0);

            OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                    i,
                    factorSuit,
                    "systemPrompt",
                    stats,
                    Instant.now().minusSeconds(10),
                    Instant.now()
            );

            builder.addIteration(OptimizationRecord.success(aggregate, 0.75));
        }

        return builder.buildPartial();
    }
}
