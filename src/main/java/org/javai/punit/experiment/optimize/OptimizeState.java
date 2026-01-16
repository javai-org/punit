package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.model.UseCaseOutcome;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable state for an OPTIMIZE experiment execution.
 *
 * <p>This class holds the state that is shared between the invocation context
 * stream generation (Spliterator) and the intercept method. It tracks:
 * <ul>
 *   <li>Current iteration and sample numbers</li>
 *   <li>Current treatment factor value</li>
 *   <li>Outcomes collected for the current iteration</li>
 *   <li>History of completed iterations</li>
 *   <li>Termination status</li>
 * </ul>
 *
 * <p>Thread safety: This class uses atomic operations for flags and counters
 * that may be accessed during stream consumption and interception.
 */
public final class OptimizeState {

    // Configuration
    private final String useCaseId;
    private final String experimentId;
    private final String treatmentFactorName;
    private final String treatmentFactorType;
    private final int samplesPerIteration;
    private final int maxIterations;
    private final OptimizationObjective objective;
    private final Scorer<OptimizationIterationAggregate> scorer;
    private final FactorMutator<Object> mutator;
    private final OptimizeTerminationPolicy terminationPolicy;
    private final FactorSuit fixedFactors;

    // Mutable state
    private final AtomicInteger currentIteration = new AtomicInteger(0);
    private final AtomicInteger currentSampleInIteration = new AtomicInteger(0);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private volatile Object currentTreatmentValue;
    private volatile Instant iterationStartTime;

    // Current iteration outcomes (reset at start of each iteration)
    private final List<UseCaseOutcome> currentIterationOutcomes = new ArrayList<>();

    // History builder
    private final OptimizeHistory.Builder historyBuilder;

    // Aggregator for outcomes
    private final OptimizationOutcomeAggregator aggregator;

    @SuppressWarnings("unchecked")
    public OptimizeState(
            String useCaseId,
            String experimentId,
            String treatmentFactorName,
            String treatmentFactorType,
            int samplesPerIteration,
            int maxIterations,
            OptimizationObjective objective,
            Scorer<OptimizationIterationAggregate> scorer,
            FactorMutator<?> mutator,
            OptimizeTerminationPolicy terminationPolicy,
            FactorSuit fixedFactors,
            Object initialTreatmentValue
    ) {
        this.useCaseId = useCaseId;
        this.experimentId = experimentId;
        this.treatmentFactorName = treatmentFactorName;
        this.treatmentFactorType = treatmentFactorType;
        this.samplesPerIteration = samplesPerIteration;
        this.maxIterations = maxIterations;
        this.objective = objective;
        this.scorer = scorer;
        this.mutator = (FactorMutator<Object>) mutator;
        this.terminationPolicy = terminationPolicy;
        this.fixedFactors = fixedFactors;
        this.currentTreatmentValue = initialTreatmentValue;
        this.iterationStartTime = Instant.now();
        this.aggregator = OptimizationOutcomeAggregator.defaultAggregator();

        this.historyBuilder = OptimizeHistory.builder()
                .useCaseId(useCaseId)
                .experimentId(experimentId)
                .treatmentFactorName(treatmentFactorName)
                .treatmentFactorType(treatmentFactorType)
                .fixedFactors(fixedFactors)
                .objective(objective)
                .scorerDescription(scorer.description())
                .mutatorDescription(mutator.description())
                .terminationPolicyDescription(terminationPolicy.description())
                .startTime(Instant.now());
    }

    // Accessors

    public String useCaseId() {
        return useCaseId;
    }

    public String experimentId() {
        return experimentId;
    }

    public String treatmentFactorName() {
        return treatmentFactorName;
    }

    public String treatmentFactorType() {
        return treatmentFactorType;
    }

    public int samplesPerIteration() {
        return samplesPerIteration;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public OptimizationObjective objective() {
        return objective;
    }

    public FactorSuit fixedFactors() {
        return fixedFactors;
    }

    public int currentIteration() {
        return currentIteration.get();
    }

    public int currentSampleInIteration() {
        return currentSampleInIteration.get();
    }

    public boolean isTerminated() {
        return terminated.get();
    }

    public void setTerminated(boolean value) {
        terminated.set(value);
    }

    public Object currentTreatmentValue() {
        return currentTreatmentValue;
    }

    public Instant iterationStartTime() {
        return iterationStartTime;
    }

    public OptimizeHistory.Builder historyBuilder() {
        return historyBuilder;
    }

    // Sample tracking

    /**
     * Advances to the next sample in the current iteration.
     *
     * @return the new sample number (1-indexed)
     */
    public int nextSample() {
        return currentSampleInIteration.incrementAndGet();
    }

    /**
     * Checks if this is the last sample of the current iteration.
     */
    public boolean isLastSampleOfIteration() {
        return currentSampleInIteration.get() >= samplesPerIteration;
    }

    /**
     * Records an outcome for the current iteration.
     */
    public synchronized void recordOutcome(UseCaseOutcome outcome) {
        currentIterationOutcomes.add(outcome);
    }

    // Iteration transition

    /**
     * Completes the current iteration: aggregates outcomes, scores, and checks termination.
     *
     * @return true if optimization should continue, false if terminated
     */
    public synchronized boolean completeIteration() {
        int iteration = currentIteration.get();
        Instant iterEnd = Instant.now();

        // Build factor suit for this iteration
        FactorSuit factorSuit = fixedFactors.with(treatmentFactorName, currentTreatmentValue);

        // Aggregate outcomes
        OptimizeStatistics statistics = aggregator.aggregate(currentIterationOutcomes);

        OptimizationIterationAggregate aggregate = new OptimizationIterationAggregate(
                iteration,
                factorSuit,
                treatmentFactorName,
                statistics,
                iterationStartTime,
                iterEnd
        );

        // Score the aggregate
        double score;
        try {
            score = scorer.score(aggregate);
        } catch (ScoringException e) {
            OptimizationRecord failed = OptimizationRecord.scoringFailed(aggregate, e.getMessage());
            historyBuilder.addIteration(failed);
            historyBuilder.endTime(Instant.now());
            historyBuilder.terminationReason(OptimizeTerminationReason.scoringFailure(e.getMessage()));
            terminated.set(true);
            return false;
        }

        // Record in history
        OptimizationRecord record = OptimizationRecord.success(aggregate, score);
        historyBuilder.addIteration(record);

        // Check termination
        OptimizeHistory currentHistory = historyBuilder.buildPartial();
        var termination = terminationPolicy.shouldTerminate(currentHistory);

        if (termination.isPresent()) {
            historyBuilder.endTime(Instant.now());
            historyBuilder.terminationReason(termination.get());
            terminated.set(true);
            return false;
        }

        // Mutate treatment factor for next iteration
        try {
            currentTreatmentValue = mutator.mutate(currentTreatmentValue, currentHistory);
            mutator.validate(currentTreatmentValue);
        } catch (MutationException e) {
            historyBuilder.endTime(Instant.now());
            historyBuilder.terminationReason(OptimizeTerminationReason.mutationFailure(e.getMessage()));
            terminated.set(true);
            return false;
        }

        // Prepare for next iteration
        currentIteration.incrementAndGet();
        currentSampleInIteration.set(0);
        currentIterationOutcomes.clear();
        iterationStartTime = Instant.now();

        return true;
    }

    /**
     * Builds the final optimization history.
     */
    public OptimizeHistory buildHistory() {
        if (!terminated.get()) {
            // If not explicitly terminated, mark as completed
            historyBuilder.endTime(Instant.now());
            historyBuilder.terminationReason(OptimizeTerminationReason.completed());
        }
        return historyBuilder.build();
    }

    /**
     * Gets the current partial history (for progress reporting).
     */
    public OptimizeHistory buildPartialHistory() {
        return historyBuilder.buildPartial();
    }
}
