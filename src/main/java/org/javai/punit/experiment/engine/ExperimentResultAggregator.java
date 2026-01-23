package org.javai.punit.experiment.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.spec.criteria.PostconditionAggregator;

/**
 * Aggregates results from experiment sample executions.
 *
 * <p>This aggregator is designed for experiments which:
 * <ul>
 *   <li>Collect {@link UseCaseOutcome} instances</li>
 *   <li>Track failure modes by category</li>
 *   <li>Compute statistical summaries</li>
 *   <li>Track token consumption</li>
 * </ul>
 */
public class ExperimentResultAggregator {

    private final String useCaseId;
    private final int totalSamples;
    private final long startTimeMs;
    private long lastSampleTimeMs;

    private int successes = 0;
    private int failures = 0;
    private long totalTokens = 0;
    private final Map<String, Integer> failureDistribution = new LinkedHashMap<>();
    private final List<UseCaseOutcome<?>> outcomes = new ArrayList<>();
    private final List<ResultProjection> resultProjections = new ArrayList<>();
    private final PostconditionAggregator postconditionAggregator = new PostconditionAggregator();
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
        this.lastSampleTimeMs = this.startTimeMs;
    }

    /**
     * Records a successful sample execution.
     *
     * @param outcome the use case outcome
     */
    public void recordSuccess(UseCaseOutcome<?> outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        successes++;
        outcomes.add(outcome);
        trackTokens(outcome);
        recordPostconditions(outcome.evaluatePostconditions());
        updateLastSampleTime();
    }

    /**
     * Records a failed sample execution.
     *
     * @param outcome the use case outcome
     * @param failureCategory the category of failure (for distribution tracking)
     */
    public void recordFailure(UseCaseOutcome<?> outcome, String failureCategory) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        failures++;
        outcomes.add(outcome);
        trackTokens(outcome);
        recordPostconditions(outcome.evaluatePostconditions());
        updateLastSampleTime();

        if (failureCategory != null && !failureCategory.isEmpty()) {
            failureDistribution.merge(failureCategory, 1, Integer::sum);
        } else {
            failureDistribution.merge("unknown", 1, Integer::sum);
        }
    }

    /**
     * Records a failure from an exception (no outcome available).
     *
     * @param exception the exception that caused the failure
     */
    public void recordException(Throwable exception) {
        failures++;
        String category = exception != null
            ? exception.getClass().getSimpleName()
            : "unknown";
        failureDistribution.merge(category, 1, Integer::sum);
        updateLastSampleTime();
    }

    /**
     * Updates the last sample completion timestamp.
     */
    private void updateLastSampleTime() {
        this.lastSampleTimeMs = System.currentTimeMillis();
    }

    /**
     * Tracks tokens from an outcome using metadata.
     */
    private void trackTokens(UseCaseOutcome<?> outcome) {
        outcome.getMetadataLong("tokensUsed", "tokens", "totalTokens")
                .ifPresent(tokens -> totalTokens += tokens);
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
     * Records postcondition outcomes for a sample.
     *
     * <p>The postcondition results are aggregated for statistical reporting.
     *
     * @param results the postcondition results to record
     */
    public void recordPostconditions(List<PostconditionResult> results) {
        if (results != null) {
            postconditionAggregator.record(results);
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

    /**
     * Returns the timestamp of the last sample completion.
     *
     * <p>This is used as the baseline end time for expiration calculation.
     *
     * @return the end time as an Instant
     */
    public java.time.Instant getEndTime() {
        return java.time.Instant.ofEpochMilli(lastSampleTimeMs);
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

    /**
     * Returns all recorded outcomes.
     *
     * @return unmodifiable list of outcomes
     */
    public List<UseCaseOutcome<?>> getOutcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    /**
     * Returns all recorded outcomes.
     *
     * @return unmodifiable list of outcomes
     * @deprecated Use {@link #getOutcomes()} instead (renamed for clarity)
     */
    @Deprecated(forRemoval = true)
    public List<UseCaseOutcome<?>> getContractOutcomes() {
        return getOutcomes();
    }

    /**
     * Adds a result projection (for EXPLORE mode).
     *
     * @param projection the result projection to add
     */
    public void addResultProjection(ResultProjection projection) {
        if (projection != null) {
            resultProjections.add(projection);
        }
    }

    /**
     * Returns all result projections.
     *
     * @return unmodifiable list of result projections
     */
    public List<ResultProjection> getResultProjections() {
        return Collections.unmodifiableList(resultProjections);
    }

    /**
     * Returns true if result projections have been recorded.
     *
     * @return true if projections exist
     */
    public boolean hasResultProjections() {
        return !resultProjections.isEmpty();
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

    /**
     * Returns the postcondition aggregator.
     *
     * @return the postcondition aggregator
     */
    public PostconditionAggregator getPostconditionAggregator() {
        return postconditionAggregator;
    }

    /**
     * Returns true if any postconditions have been recorded.
     *
     * @return true if postcondition stats are available
     */
    public boolean hasPostconditionStats() {
        return postconditionAggregator.getSamplesRecorded() > 0;
    }

    /**
     * Returns a summary of postcondition pass rates.
     *
     * <p>This is a convenience method that returns a map of postcondition
     * descriptions to their observed pass rates. Useful for spec generation.
     *
     * @return map of postcondition descriptions to pass rates
     */
    public Map<String, Double> getPostconditionPassRates() {
        return postconditionAggregator.getPassRateSummary();
    }
}
