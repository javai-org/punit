package org.javai.punit.experiment.optimize;

import org.javai.punit.experiment.model.FactorSuit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Complete history of an optimization run.
 *
 * <p>This is the primary output of OPTIMIZE mode. Unlike:
 * <ul>
 *   <li>MEASURE: produces a baseline document (statistics)</li>
 *   <li>EXPLORE: produces specs for human comparison via diff</li>
 * </ul>
 *
 * <p>OPTIMIZE produces:
 * <ul>
 *   <li>The best value found for the treatment factor (primary output)</li>
 *   <li>Full iteration history (for auditability and analysis)</li>
 *   <li>Termination reason (why optimization stopped)</li>
 * </ul>
 *
 * <p>The history is immutable once built. Use {@link Builder} for construction.
 */
public final class OptimizeHistory {

    // === Identification ===
    private final String useCaseId;
    private final String experimentId;

    // === Factor Configuration ===
    private final String treatmentFactorName;
    private final String treatmentFactorType;
    private final FactorSuit fixedFactors;
    private final OptimizationObjective objective;

    // === Component Descriptions ===
    private final String scorerDescription;
    private final String mutatorDescription;
    private final String terminationPolicyDescription;

    // === Timing ===
    private final Instant startTime;
    private final Instant endTime;

    // === Iterations ===
    private final List<OptimizationRecord> iterations;

    // === Termination ===
    private final OptimizeTerminationReason terminationReason;

    private OptimizeHistory(Builder builder) {
        this.useCaseId = builder.useCaseId;
        this.experimentId = builder.experimentId;
        this.treatmentFactorName = builder.treatmentFactorName;
        this.treatmentFactorType = builder.treatmentFactorType;
        this.fixedFactors = builder.fixedFactors;
        this.objective = builder.objective;
        this.scorerDescription = builder.scorerDescription;
        this.mutatorDescription = builder.mutatorDescription;
        this.terminationPolicyDescription = builder.terminationPolicyDescription;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.iterations = List.copyOf(builder.iterations);
        this.terminationReason = builder.terminationReason;
    }

    // === Getters ===

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

    public FactorSuit fixedFactors() {
        return fixedFactors;
    }

    public OptimizationObjective objective() {
        return objective;
    }

    public String scorerDescription() {
        return scorerDescription;
    }

    public String mutatorDescription() {
        return mutatorDescription;
    }

    public String terminationPolicyDescription() {
        return terminationPolicyDescription;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public List<OptimizationRecord> iterations() {
        return iterations;
    }

    public OptimizeTerminationReason terminationReason() {
        return terminationReason;
    }

    // === Query Methods ===

    /**
     * Get the number of iterations completed.
     *
     * @return the iteration count
     */
    public int iterationCount() {
        return iterations.size();
    }

    /**
     * Get the total duration of the optimization run.
     *
     * @return the duration, or Duration.ZERO if not yet complete
     */
    public Duration totalDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /**
     * Get the last N iterations.
     *
     * @param n the number of iterations to return
     * @return the last N iterations (or all if fewer than N exist)
     */
    public List<OptimizationRecord> lastNIterations(int n) {
        int start = Math.max(0, iterations.size() - n);
        return iterations.subList(start, iterations.size());
    }

    /**
     * Get all successful iterations.
     *
     * @return list of successful iterations
     */
    public List<OptimizationRecord> successfulIterations() {
        return iterations.stream()
                .filter(OptimizationRecord::isSuccessful)
                .toList();
    }

    /**
     * Find the best iteration according to the objective.
     *
     * @return the best iteration, or empty if no successful iterations
     */
    public Optional<OptimizationRecord> bestIteration() {
        List<OptimizationRecord> successful = successfulIterations();
        if (successful.isEmpty()) {
            return Optional.empty();
        }

        Comparator<OptimizationRecord> comparator = Comparator.comparingDouble(OptimizationRecord::score);
        if (objective == OptimizationObjective.MAXIMIZE) {
            comparator = comparator.reversed();
        }

        return successful.stream().min(comparator);
    }

    /**
     * Get the best value found for the treatment factor.
     *
     * @param <F> the type of the treatment factor
     * @return the best value, or empty if no successful iterations
     */
    @SuppressWarnings("unchecked")
    public <F> Optional<F> bestFactorValue() {
        return bestIteration()
                .map(iter -> (F) iter.aggregate().treatmentFactorValue());
    }

    /**
     * Get the best score achieved.
     *
     * @return the best score, or empty if no successful iterations
     */
    public Optional<Double> bestScore() {
        return bestIteration().map(OptimizationRecord::score);
    }

    /**
     * Get the initial score (first iteration).
     *
     * @return the initial score, or empty if no successful iterations
     */
    public Optional<Double> initialScore() {
        return iterations.stream()
                .filter(OptimizationRecord::isSuccessful)
                .findFirst()
                .map(OptimizationRecord::score);
    }

    /**
     * Calculate score improvement from first to best iteration.
     *
     * @return the improvement, or 0.0 if not calculable
     */
    public double scoreImprovement() {
        Optional<Double> initial = initialScore();
        Optional<Double> best = bestScore();
        if (initial.isEmpty() || best.isEmpty()) {
            return 0.0;
        }
        return best.get() - initial.get();
    }

    /**
     * Calculate score improvement as a percentage.
     *
     * @return the improvement percentage, or 0.0 if not calculable
     */
    public double scoreImprovementPercent() {
        Optional<Double> initial = initialScore();
        if (initial.isEmpty() || initial.get() == 0.0) {
            return 0.0;
        }
        return (scoreImprovement() / Math.abs(initial.get())) * 100.0;
    }

    /**
     * Get total tokens consumed across all iterations.
     *
     * @return total token count
     */
    public long totalTokens() {
        return iterations.stream()
                .mapToLong(iter -> iter.aggregate().statistics().totalTokens())
                .sum();
    }

    /**
     * Create a new builder.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OptimizeHistory.
     *
     * <p>Supports incremental construction during the optimization loop.
     * Use {@link #buildPartial()} to query the history mid-optimization.
     */
    public static final class Builder {

        private String useCaseId;
        private String experimentId;
        private String treatmentFactorName;
        private String treatmentFactorType;
        private FactorSuit fixedFactors;
        private OptimizationObjective objective = OptimizationObjective.MAXIMIZE;
        private String scorerDescription = "";
        private String mutatorDescription = "";
        private String terminationPolicyDescription = "";
        private Instant startTime;
        private Instant endTime;
        private final List<OptimizationRecord> iterations = new ArrayList<>();
        private OptimizeTerminationReason terminationReason;

        private Builder() {}

        public Builder useCaseId(String useCaseId) {
            this.useCaseId = useCaseId;
            return this;
        }

        public Builder experimentId(String experimentId) {
            this.experimentId = experimentId;
            return this;
        }

        public Builder treatmentFactorName(String treatmentFactorName) {
            this.treatmentFactorName = treatmentFactorName;
            return this;
        }

        public Builder treatmentFactorType(String treatmentFactorType) {
            this.treatmentFactorType = treatmentFactorType;
            return this;
        }

        public Builder fixedFactors(FactorSuit fixedFactors) {
            this.fixedFactors = fixedFactors;
            return this;
        }

        public Builder objective(OptimizationObjective objective) {
            this.objective = objective;
            return this;
        }

        public Builder scorerDescription(String scorerDescription) {
            this.scorerDescription = scorerDescription;
            return this;
        }

        public Builder mutatorDescription(String mutatorDescription) {
            this.mutatorDescription = mutatorDescription;
            return this;
        }

        public Builder terminationPolicyDescription(String terminationPolicyDescription) {
            this.terminationPolicyDescription = terminationPolicyDescription;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder terminationReason(OptimizeTerminationReason terminationReason) {
            this.terminationReason = terminationReason;
            return this;
        }

        /**
         * Add an iteration record to the history.
         *
         * @param record the iteration to add
         * @return this builder
         */
        public Builder addIteration(OptimizationRecord record) {
            this.iterations.add(record);
            return this;
        }

        /**
         * Get the current iteration count.
         *
         * @return number of iterations added so far
         */
        public int iterationCount() {
            return iterations.size();
        }

        /**
         * Get a read-only view of iterations added so far.
         *
         * @return unmodifiable list of iterations
         */
        public List<OptimizationRecord> iterations() {
            return Collections.unmodifiableList(iterations);
        }

        /**
         * Build a partial history for mid-optimization queries.
         *
         * <p>This allows termination policies and mutators to query the
         * history during optimization without finalizing it.
         *
         * @return an OptimizeHistory representing the current state
         */
        public OptimizeHistory buildPartial() {
            return new OptimizeHistory(this);
        }

        /**
         * Build the final, complete history.
         *
         * @return the completed OptimizeHistory
         * @throws IllegalStateException if required fields are missing
         */
        public OptimizeHistory build() {
            validate();
            return new OptimizeHistory(this);
        }

        private void validate() {
            if (useCaseId == null || useCaseId.isBlank()) {
                throw new IllegalStateException("useCaseId is required");
            }
            if (treatmentFactorName == null || treatmentFactorName.isBlank()) {
                throw new IllegalStateException("treatmentFactorName is required");
            }
            if (objective == null) {
                throw new IllegalStateException("objective is required");
            }
            if (startTime == null) {
                throw new IllegalStateException("startTime is required");
            }
        }
    }
}
