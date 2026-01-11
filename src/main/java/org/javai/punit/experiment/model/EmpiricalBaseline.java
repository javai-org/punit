package org.javai.punit.experiment.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an empirical baseline generated from experiment execution.
 *
 * <p>A baseline is a machine-readable record of observed behavior:
 * <ul>
 *   <li>Observed success rates and variance</li>
 *   <li>Failure mode distribution</li>
 *   <li>Cost metrics (tokens, time)</li>
 *   <li>Execution context metadata</li>
 * </ul>
 *
 * <p>Baselines are immutable and auditable. They are descriptive (what happened),
 * not normative (what should happen).
 */
public final class EmpiricalBaseline {
    
    private final String useCaseId;
    private final String experimentId;
    private final Instant generatedAt;
    private final String experimentClass;
    private final String experimentMethod;
    private final Map<String, Object> context;
    private final ExecutionSummary execution;
    private final StatisticsSummary statistics;
    private final CostSummary cost;
    private final String successCriteriaDefinition;
    private final List<ResultProjection> resultProjections;
    
    private EmpiricalBaseline(Builder builder) {
        this.useCaseId = Objects.requireNonNull(builder.useCaseId, "useCaseId must not be null");
        this.experimentId = builder.experimentId;
        this.generatedAt = builder.generatedAt != null ? builder.generatedAt : Instant.now();
        this.experimentClass = builder.experimentClass;
        this.experimentMethod = builder.experimentMethod;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(builder.context));
        this.execution = Objects.requireNonNull(builder.execution, "execution must not be null");
        this.statistics = Objects.requireNonNull(builder.statistics, "statistics must not be null");
        this.cost = Objects.requireNonNull(builder.cost, "cost must not be null");
        this.successCriteriaDefinition = builder.successCriteriaDefinition;
        this.resultProjections = Collections.unmodifiableList(new ArrayList<>(builder.resultProjections));
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getUseCaseId() {
        return useCaseId;
    }
    
    public String getExperimentId() {
        return experimentId;
    }
    
    public Instant getGeneratedAt() {
        return generatedAt;
    }
    
    public String getExperimentClass() {
        return experimentClass;
    }
    
    public String getExperimentMethod() {
        return experimentMethod;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public ExecutionSummary getExecution() {
        return execution;
    }
    
    public StatisticsSummary getStatistics() {
        return statistics;
    }
    
    public CostSummary getCost() {
        return cost;
    }
    
    public String getSuccessCriteriaDefinition() {
        return successCriteriaDefinition;
    }
    
    /**
     * Returns result projections for EXPLORE mode.
     * Empty for MEASURE mode.
     *
     * @return unmodifiable list of result projections
     */
    public List<ResultProjection> getResultProjections() {
        return resultProjections;
    }
    
    /**
     * Returns true if this baseline has result projections.
     *
     * @return true if result projections are present
     */
    public boolean hasResultProjections() {
        return resultProjections != null && !resultProjections.isEmpty();
    }

	/**
	 * Summary of experiment execution.
	 */
	public record ExecutionSummary(int samplesPlanned, int samplesExecuted, String terminationReason,
								   String terminationDetails) {
	}

	/**
	 * Statistical summary of experiment results.
	 */
	public record StatisticsSummary(double observedSuccessRate, double standardError, double confidenceIntervalLower,
									double confidenceIntervalUpper, int successes, int failures,
									Map<String, Integer> failureDistribution) {
		public StatisticsSummary(double observedSuccessRate, double standardError,
								 double confidenceIntervalLower, double confidenceIntervalUpper,
								 int successes, int failures,
								 Map<String, Integer> failureDistribution) {
			this.observedSuccessRate = observedSuccessRate;
			this.standardError = standardError;
			this.confidenceIntervalLower = confidenceIntervalLower;
			this.confidenceIntervalUpper = confidenceIntervalUpper;
			this.successes = successes;
			this.failures = failures;
			this.failureDistribution = failureDistribution != null
					? Collections.unmodifiableMap(new LinkedHashMap<>(failureDistribution))
					: Collections.emptyMap();
		}
	}

	/**
	 * Cost metrics from experiment execution.
	 */
	public record CostSummary(long totalTimeMs, long avgTimePerSampleMs, long totalTokens, long avgTokensPerSample) {
	}
    
    public static final class Builder {
        private String useCaseId;
        private String experimentId;
        private Instant generatedAt;
        private String experimentClass;
        private String experimentMethod;
        private final Map<String, Object> context = new LinkedHashMap<>();
        private ExecutionSummary execution;
        private StatisticsSummary statistics;
        private CostSummary cost;
        private String successCriteriaDefinition;
        private final List<ResultProjection> resultProjections = new ArrayList<>();
        
        private Builder() {}
        
        public Builder useCaseId(String useCaseId) {
            this.useCaseId = useCaseId;
            return this;
        }
        
        public Builder experimentId(String experimentId) {
            this.experimentId = experimentId;
            return this;
        }
        
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }
        
        public Builder experimentClass(String experimentClass) {
            this.experimentClass = experimentClass;
            return this;
        }
        
        public Builder experimentMethod(String experimentMethod) {
            this.experimentMethod = experimentMethod;
            return this;
        }
        
        public Builder context(String key, Object value) {
            this.context.put(key, value);
            return this;
        }
        
        public Builder context(Map<String, Object> context) {
            if (context != null) {
                this.context.putAll(context);
            }
            return this;
        }
        
        public Builder execution(ExecutionSummary execution) {
            this.execution = execution;
            return this;
        }
        
        public Builder statistics(StatisticsSummary statistics) {
            this.statistics = statistics;
            return this;
        }
        
        public Builder cost(CostSummary cost) {
            this.cost = cost;
            return this;
        }
        
        public Builder successCriteriaDefinition(String definition) {
            this.successCriteriaDefinition = definition;
            return this;
        }
        
        /**
         * Adds a result projection to the baseline.
         *
         * @param projection the result projection to add
         * @return this builder
         */
        public Builder resultProjection(ResultProjection projection) {
            if (projection != null) {
                this.resultProjections.add(projection);
            }
            return this;
        }
        
        /**
         * Adds all result projections to the baseline.
         *
         * @param projections the result projections to add
         * @return this builder
         */
        public Builder resultProjections(List<ResultProjection> projections) {
            if (projections != null) {
                this.resultProjections.addAll(projections);
            }
            return this;
        }
        
        public EmpiricalBaseline build() {
            return new EmpiricalBaseline(this);
        }
    }
}

