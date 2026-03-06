package org.javai.punit.experiment.engine;

import org.javai.punit.api.ExperimentGoal;

/**
 * Evaluates whether an experiment goal has been achieved.
 */
public class GoalEvaluator {
    
    private final double targetSuccessRate;
    private final long maxLatencyMs;
    private final long maxTokensPerSample;
    private final String customExpression;
    
    /**
     * Creates a goal evaluator from an annotation.
     *
     * @param goal the goal annotation
     */
    public GoalEvaluator(ExperimentGoal goal) {
        this.targetSuccessRate = goal != null ? goal.successRate() : Double.NaN;
        this.maxLatencyMs = goal != null ? goal.maxLatencyMs() : Long.MAX_VALUE;
        this.maxTokensPerSample = goal != null ? goal.maxTokensPerSample() : Long.MAX_VALUE;
        this.customExpression = goal != null ? goal.when() : "";
    }
    
    /**
     * Creates a goal evaluator with the given criteria.
     *
     * @param targetSuccessRate the minimum success rate (NaN to ignore)
     * @param maxLatencyMs the maximum average latency (MAX_VALUE to ignore)
     * @param maxTokensPerSample the maximum average tokens (MAX_VALUE to ignore)
     */
    public GoalEvaluator(double targetSuccessRate, long maxLatencyMs, long maxTokensPerSample) {
        this.targetSuccessRate = targetSuccessRate;
        this.maxLatencyMs = maxLatencyMs;
        this.maxTokensPerSample = maxTokensPerSample;
        this.customExpression = "";
    }
    
    /**
     * Creates a goal evaluator with only a success rate target.
     *
     * @param targetSuccessRate the minimum success rate
     * @return the evaluator
     */
    public static GoalEvaluator forSuccessRate(double targetSuccessRate) {
        return new GoalEvaluator(targetSuccessRate, Long.MAX_VALUE, Long.MAX_VALUE);
    }
    
    /**
     * Creates a goal evaluator with no goals (never triggers early termination).
     *
     * @return the no-op evaluator
     */
    public static GoalEvaluator none() {
        return new GoalEvaluator(Double.NaN, Long.MAX_VALUE, Long.MAX_VALUE);
    }
    
    /**
     * Evaluates whether the goal has been achieved.
     *
     * @param aggregator the result aggregator to evaluate
     * @return true if the goal is met
     */
    public boolean isGoalMet(ExperimentResultAggregator aggregator) {
        if (!hasGoal()) {
            return false;
        }
        
        // Check success rate
        if (!Double.isNaN(targetSuccessRate)) {
            if (aggregator.getObservedSuccessRate() < targetSuccessRate) {
                return false;
            }
        }
        
        // Check latency
        if (maxLatencyMs < Long.MAX_VALUE) {
            if (aggregator.getAvgTimePerSampleMs() > maxLatencyMs) {
                return false;
            }
        }
        
        // Check tokens
        if (maxTokensPerSample < Long.MAX_VALUE) {
			return aggregator.getAvgTokensPerSample() <= maxTokensPerSample;
        }
        
        // All applicable criteria met
        return true;
    }
    
    /**
     * Returns true if this evaluator has any goal criteria.
     *
     * @return true if there are goals to evaluate
     */
    public boolean hasGoal() {
        return !Double.isNaN(targetSuccessRate) ||
               maxLatencyMs < Long.MAX_VALUE ||
               maxTokensPerSample < Long.MAX_VALUE ||
               (customExpression != null && !customExpression.isEmpty());
    }
    
    /**
     * Returns a description of the goal criteria.
     *
     * @return goal description
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        
        if (!Double.isNaN(targetSuccessRate)) {
            sb.append("successRate >= ").append(String.format("%.2f", targetSuccessRate));
        }
        if (maxLatencyMs < Long.MAX_VALUE) {
            if (sb.length() > 0) sb.append(" && ");
            sb.append("avgLatencyMs <= ").append(maxLatencyMs);
        }
        if (maxTokensPerSample < Long.MAX_VALUE) {
            if (sb.length() > 0) sb.append(" && ");
            sb.append("avgTokensPerSample <= ").append(maxTokensPerSample);
        }
        if (customExpression != null && !customExpression.isEmpty()) {
            if (sb.length() > 0) sb.append(" && ");
            sb.append(customExpression);
        }
        
        return sb.length() > 0 ? sb.toString() : "(no goal)";
    }
    
    public double getTargetSuccessRate() {
        return targetSuccessRate;
    }
    
    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }
    
    public long getMaxTokensPerSample() {
        return maxTokensPerSample;
    }
}

