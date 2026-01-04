package org.javai.punit.experiment.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.experiment.model.UseCaseResult;

/**
 * Aggregates results from experiment sample executions.
 *
 * <p>Unlike {@link org.javai.punit.engine.SampleResultAggregator}, this aggregator
 * is designed for experiments which:
 * <ul>
 *   <li>Collect {@link UseCaseResult} instances (not just pass/fail)</li>
 *   <li>Track failure modes by category</li>
 *   <li>Compute statistical summaries</li>
 *   <li>Track token consumption</li>
 * </ul>
 */
public class ExperimentResultAggregator {
    
    private final String useCaseId;
    private final int totalSamples;
    private final long startTimeMs;
    
    private int successes = 0;
    private int failures = 0;
    private long totalTokens = 0;
    private final Map<String, Integer> failureDistribution = new LinkedHashMap<>();
    private final List<UseCaseResult> results = new ArrayList<>();
    private String terminationReason = null;
    private String terminationDetails = null;
    
    /**
     * Creates a new aggregator for an experiment.
     *
     * @param useCaseId the use case ID being executed
     * @param totalSamples the planned number of samples
     */
    public ExperimentResultAggregator(String useCaseId, int totalSamples) {
        this.useCaseId = Objects.requireNonNull(useCaseId, "useCaseId must not be null");
        this.totalSamples = totalSamples;
        this.startTimeMs = System.currentTimeMillis();
    }
    
    /**
     * Records a successful sample execution.
     *
     * @param result the use case result
     */
    public void recordSuccess(UseCaseResult result) {
        Objects.requireNonNull(result, "result must not be null");
        successes++;
        results.add(result);
        trackTokens(result);
    }
    
    /**
     * Records a failed sample execution.
     *
     * @param result the use case result
     * @param failureCategory the category of failure (for distribution tracking)
     */
    public void recordFailure(UseCaseResult result, String failureCategory) {
        Objects.requireNonNull(result, "result must not be null");
        failures++;
        results.add(result);
        trackTokens(result);
        
        if (failureCategory != null && !failureCategory.isEmpty()) {
            failureDistribution.merge(failureCategory, 1, Integer::sum);
        } else {
            failureDistribution.merge("unknown", 1, Integer::sum);
        }
    }
    
    /**
     * Records a failure from an exception (no UseCaseResult available).
     *
     * @param exception the exception that caused the failure
     */
    public void recordException(Throwable exception) {
        failures++;
        String category = exception != null 
            ? exception.getClass().getSimpleName() 
            : "unknown";
        failureDistribution.merge(category, 1, Integer::sum);
    }
    
    private void trackTokens(UseCaseResult result) {
        // Look for common token count keys
        long tokens = result.getLong("tokensUsed", 0);
        if (tokens == 0) {
            tokens = result.getLong("tokens", 0);
        }
        if (tokens == 0) {
            tokens = result.getLong("totalTokens", 0);
        }
        totalTokens += tokens;
    }
    
    /**
     * Adds tokens to the total count (for external token tracking).
     *
     * @param tokens the number of tokens to add
     */
    public void addTokens(long tokens) {
        if (tokens > 0) {
            totalTokens += tokens;
        }
    }
    
    /**
     * Marks the experiment as terminated.
     *
     * @param reason the termination reason
     * @param details optional details
     */
    public void setTerminated(String reason, String details) {
        this.terminationReason = reason;
        this.terminationDetails = details;
    }
    
    /**
     * Marks the experiment as completed.
     */
    public void setCompleted() {
        this.terminationReason = "COMPLETED";
        this.terminationDetails = null;
    }
    
    // Getters
    
    public String getUseCaseId() {
        return useCaseId;
    }
    
    public int getTotalSamples() {
        return totalSamples;
    }
    
    public int getSuccesses() {
        return successes;
    }
    
    public int getFailures() {
        return failures;
    }
    
    public int getSamplesExecuted() {
        return successes + failures;
    }
    
    public double getObservedSuccessRate() {
        int executed = getSamplesExecuted();
        if (executed == 0) {
            return 0.0;
        }
        return (double) successes / executed;
    }
    
    /**
     * Computes the standard error of the success rate.
     *
     * <p>Uses the formula: SE = sqrt(p * (1-p) / n)
     *
     * @return the standard error
     */
    public double getStandardError() {
        int n = getSamplesExecuted();
        if (n < 2) {
            return 0.0;
        }
        double p = getObservedSuccessRate();
        return Math.sqrt(p * (1 - p) / n);
    }
    
    /**
     * Computes the 95% confidence interval for the success rate.
     *
     * <p>Uses: p ± 1.96 * SE (normal approximation)
     *
     * @return array of [lower, upper] bounds
     */
    public double[] getConfidenceInterval95() {
        double p = getObservedSuccessRate();
        double se = getStandardError();
        double margin = 1.96 * se;
        return new double[] {
            Math.max(0.0, p - margin),
            Math.min(1.0, p + margin)
        };
    }
    
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
    
    public long getTotalTokens() {
        return totalTokens;
    }
    
    public long getAvgTokensPerSample() {
        int executed = getSamplesExecuted();
        if (executed == 0) {
            return 0;
        }
        return totalTokens / executed;
    }
    
    public long getAvgTimePerSampleMs() {
        int executed = getSamplesExecuted();
        if (executed == 0) {
            return 0;
        }
        return getElapsedMs() / executed;
    }
    
    public Map<String, Integer> getFailureDistribution() {
        return Collections.unmodifiableMap(failureDistribution);
    }
    
    public List<UseCaseResult> getResults() {
        return Collections.unmodifiableList(results);
    }
    
    public String getTerminationReason() {
        return terminationReason;
    }
    
    public String getTerminationDetails() {
        return terminationDetails;
    }
    
    public boolean isComplete() {
        return terminationReason != null || getSamplesExecuted() >= totalSamples;
    }
    
    public int getRemainingSamples() {
        return Math.max(0, totalSamples - getSamplesExecuted());
    }
}

