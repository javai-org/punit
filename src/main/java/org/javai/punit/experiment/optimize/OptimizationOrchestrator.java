package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.model.UseCaseOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Executes the OPTIMIZE experiment loop.
 *
 * <p>The loop is conceptually simple:
 * <ol>
 *   <li>Execute use case N times (like MEASURE)</li>
 *   <li>Aggregate outcomes</li>
 *   <li>Score the aggregate</li>
 *   <li>Record in history</li>
 *   <li>Check termination</li>
 *   <li>Mutate the treatment factor</li>
 *   <li>Repeat</li>
 * </ol>
 *
 * <p>The orchestrator coordinates these steps but delegates the actual
 * work to the {@link Scorer}, {@link FactorMutator}, and {@link OptimizeTerminationPolicy}.
 *
 * @param <F> the type of the treatment factor
 */
public final class OptimizationOrchestrator<F> {

    private final OptimizationConfig<F> config;
    private final UseCaseExecutor executor;
    private final OptimizationOutcomeAggregator aggregator;
    private final Consumer<OptimizationRecord> progressCallback;

    /**
     * Creates an orchestrator with the given configuration.
     *
     * @param config the optimization configuration
     * @param executor the use case executor
     */
    public OptimizationOrchestrator(OptimizationConfig<F> config, UseCaseExecutor executor) {
        this(config, executor, OptimizationOutcomeAggregator.defaultAggregator(), record -> {});
    }

    /**
     * Creates an orchestrator with a custom aggregator.
     *
     * @param config the optimization configuration
     * @param executor the use case executor
     * @param aggregator the outcome aggregator
     */
    public OptimizationOrchestrator(
            OptimizationConfig<F> config,
            UseCaseExecutor executor,
            OptimizationOutcomeAggregator aggregator
    ) {
        this(config, executor, aggregator, record -> {});
    }

    /**
     * Creates an orchestrator with progress callback.
     *
     * @param config the optimization configuration
     * @param executor the use case executor
     * @param aggregator the outcome aggregator
     * @param progressCallback callback invoked after each iteration
     */
    public OptimizationOrchestrator(
            OptimizationConfig<F> config,
            UseCaseExecutor executor,
            OptimizationOutcomeAggregator aggregator,
            Consumer<OptimizationRecord> progressCallback
    ) {
        this.config = config;
        this.executor = executor;
        this.aggregator = aggregator;
        this.progressCallback = progressCallback != null ? progressCallback : record -> {};
    }

    /**
     * Execute the full optimization loop.
     *
     * @return complete optimization history including best factor value
     */
    public OptimizeHistory run() {
        OptimizeHistory.Builder historyBuilder = OptimizeHistory.builder()
                .useCaseId(config.useCaseId())
                .experimentId(config.experimentId())
                .treatmentFactorName(config.treatmentFactorName())
                .treatmentFactorType(config.treatmentFactorType())
                .fixedFactors(config.fixedFactors())
                .objective(config.objective())
                .scorerDescription(config.scorer().description())
                .mutatorDescription(config.mutator().description())
                .terminationPolicyDescription(config.terminationPolicy().description())
                .startTime(Instant.now());

        F currentFactorValue = config.initialFactorValue();
        int iteration = 0;

        while (true) {
            Instant iterStart = Instant.now();

            // 1. Build complete factor suit for this iteration
            FactorSuit factorSuit = config.fixedFactors()
                    .with(config.treatmentFactorName(), currentFactorValue);

            // 2. Execute use case N times (like MEASURE)
            List<UseCaseOutcome> outcomes;
            try {
                outcomes = executor.execute(factorSuit, config.samplesPerIteration());
            } catch (UseCaseExecutor.ExecutionException e) {
                // Catastrophic execution failure - terminate
                OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                        iteration,
                        factorSuit,
                        config.treatmentFactorName(),
                        OptimizeStatistics.empty(),
                        iterStart,
                        Instant.now()
                );
                OptimizationRecord failed = OptimizationRecord.executionFailed(aggregate, e.getMessage());
                historyBuilder.addIteration(failed);
                progressCallback.accept(failed);
                return historyBuilder
                        .endTime(Instant.now())
                        .terminationReason(OptimizeTerminationReason.scoringFailure(e.getMessage()))
                        .build();
            }

            // 3. Aggregate outcomes
            OptimizeStatistics statistics = aggregator.aggregate(outcomes);

            OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                    iteration,
                    factorSuit,
                    config.treatmentFactorName(),
                    statistics,
                    iterStart,
                    Instant.now()
            );

            // 4. Score the aggregate
            double score;
            try {
                score = config.scorer().score(aggregate);
            } catch (ScoringException e) {
                OptimizationRecord failed = OptimizationRecord.scoringFailed(aggregate, e.getMessage());
                historyBuilder.addIteration(failed);
                progressCallback.accept(failed);
                return historyBuilder
                        .endTime(Instant.now())
                        .terminationReason(OptimizeTerminationReason.scoringFailure(e.getMessage()))
                        .build();
            }

            // 5. Record in history
            OptimizationRecord record = OptimizationRecord.success(aggregate, score);
            historyBuilder.addIteration(record);
            progressCallback.accept(record);

            // 6. Check termination
            OptimizeHistory currentHistory = historyBuilder.buildPartial();
            Optional<OptimizeTerminationReason> termination =
                    config.terminationPolicy().shouldTerminate(currentHistory);

            if (termination.isPresent()) {
                return historyBuilder
                        .endTime(Instant.now())
                        .terminationReason(termination.get())
                        .build();
            }

            // 7. Mutate treatment factor for next iteration
            try {
                currentFactorValue = config.mutator().mutate(currentFactorValue, currentHistory);
                config.mutator().validate(currentFactorValue);
            } catch (MutationException e) {
                return historyBuilder
                        .endTime(Instant.now())
                        .terminationReason(OptimizeTerminationReason.mutationFailure(e.getMessage()))
                        .build();
            }

            iteration++;
        }
    }
}
