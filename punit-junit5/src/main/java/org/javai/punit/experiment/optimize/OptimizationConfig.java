package org.javai.punit.experiment.optimize;

import java.util.Objects;
import org.javai.punit.experiment.model.FactorSuit;

/**
 * Configuration for the optimization orchestrator.
 *
 * <p>Contains all the settings and pluggable components needed to run
 * an optimization loop.
 *
 * @param <F> the type of the control factor
 */
public final class OptimizationConfig<F> {

    private final String useCaseId;
    private final String experimentId;
    private final String controlFactorName;
    private final String controlFactorType;
    private final F initialFactorValue;
    private final FactorSuit fixedFactors;
    private final OptimizationObjective objective;
    private final Scorer<OptimizationIterationAggregate> scorer;
    private final FactorMutator<F> mutator;
    private final OptimizeTerminationPolicy terminationPolicy;
    private final int samplesPerIteration;

    private OptimizationConfig(Builder<F> builder) {
        this.useCaseId = builder.useCaseId;
        this.experimentId = builder.experimentId;
        this.controlFactorName = builder.controlFactorName;
        this.controlFactorType = builder.controlFactorType;
        this.initialFactorValue = builder.initialFactorValue;
        this.fixedFactors = builder.fixedFactors;
        this.objective = builder.objective;
        this.scorer = builder.scorer;
        this.mutator = builder.mutator;
        this.terminationPolicy = builder.terminationPolicy;
        this.samplesPerIteration = builder.samplesPerIteration;
    }

    public String useCaseId() {
        return useCaseId;
    }

    public String experimentId() {
        return experimentId;
    }

    public String controlFactorName() {
        return controlFactorName;
    }

    public String controlFactorType() {
        return controlFactorType;
    }

    public F initialFactorValue() {
        return initialFactorValue;
    }

    public FactorSuit fixedFactors() {
        return fixedFactors;
    }

    public OptimizationObjective objective() {
        return objective;
    }

    public Scorer<OptimizationIterationAggregate> scorer() {
        return scorer;
    }

    public FactorMutator<F> mutator() {
        return mutator;
    }

    public OptimizeTerminationPolicy terminationPolicy() {
        return terminationPolicy;
    }

    public int samplesPerIteration() {
        return samplesPerIteration;
    }

    /**
     * Creates a new builder.
     *
     * @param <F> the type of the control factor
     * @return a new builder
     */
    public static <F> Builder<F> builder() {
        return new Builder<>();
    }

    /**
     * Builder for OptimizationConfig.
     *
     * @param <F> the type of the control factor
     */
    public static final class Builder<F> {

        private String useCaseId;
        private String experimentId = "";
        private String controlFactorName;
        private String controlFactorType = "Object";
        private F initialFactorValue;
        private FactorSuit fixedFactors = FactorSuit.empty();
        private OptimizationObjective objective = OptimizationObjective.MAXIMIZE;
        private Scorer<OptimizationIterationAggregate> scorer;
        private FactorMutator<F> mutator;
        private OptimizeTerminationPolicy terminationPolicy;
        private int samplesPerIteration = 20;

        private Builder() {}

        public Builder<F> useCaseId(String useCaseId) {
            this.useCaseId = useCaseId;
            return this;
        }

        public Builder<F> experimentId(String experimentId) {
            this.experimentId = experimentId;
            return this;
        }

        public Builder<F> controlFactorName(String controlFactorName) {
            this.controlFactorName = controlFactorName;
            return this;
        }

        public Builder<F> controlFactorType(String controlFactorType) {
            this.controlFactorType = controlFactorType;
            return this;
        }

        public Builder<F> controlFactorType(Class<F> type) {
            this.controlFactorType = type.getSimpleName();
            return this;
        }

        public Builder<F> initialFactorValue(F initialFactorValue) {
            this.initialFactorValue = initialFactorValue;
            return this;
        }

        public Builder<F> fixedFactors(FactorSuit fixedFactors) {
            this.fixedFactors = fixedFactors;
            return this;
        }

        public Builder<F> objective(OptimizationObjective objective) {
            this.objective = objective;
            return this;
        }

        public Builder<F> scorer(Scorer<OptimizationIterationAggregate> scorer) {
            this.scorer = scorer;
            return this;
        }

        public Builder<F> mutator(FactorMutator<F> mutator) {
            this.mutator = mutator;
            return this;
        }

        public Builder<F> terminationPolicy(OptimizeTerminationPolicy terminationPolicy) {
            this.terminationPolicy = terminationPolicy;
            return this;
        }

        public Builder<F> samplesPerIteration(int samplesPerIteration) {
            this.samplesPerIteration = samplesPerIteration;
            return this;
        }

        /**
         * Build the configuration.
         *
         * @return the configuration
         * @throws IllegalStateException if required fields are missing
         */
        public OptimizationConfig<F> build() {
            Objects.requireNonNull(useCaseId, "useCaseId is required");
            Objects.requireNonNull(controlFactorName, "controlFactorName is required");
            Objects.requireNonNull(initialFactorValue, "initialFactorValue is required");
            Objects.requireNonNull(scorer, "scorer is required");
            Objects.requireNonNull(mutator, "mutator is required");
            Objects.requireNonNull(terminationPolicy, "terminationPolicy is required");
            if (samplesPerIteration < 1) {
                throw new IllegalStateException("samplesPerIteration must be at least 1");
            }
            return new OptimizationConfig<>(this);
        }
    }
}
