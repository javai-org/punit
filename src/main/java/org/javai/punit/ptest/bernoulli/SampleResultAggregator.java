package org.javai.punit.ptest.bernoulli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.javai.punit.model.TerminationReason;

/**
 * Accumulates results from individual sample executions of a probabilistic test.
 *
 * <p>This class tracks:
 * <ul>
 *   <li>Total number of samples executed</li>
 *   <li>Number of successful samples</li>
 *   <li>Number of failed samples</li>
 *   <li>Example failure causes (up to a configurable maximum)</li>
 *   <li>Elapsed time</li>
 *   <li>Termination reason (if terminated early)</li>
 * </ul>
 *
 * <p>This class is not thread-safe. For Phase 1, samples execute sequentially.
 */
public class SampleResultAggregator {

    private final int totalSamples;
    private final int maxExampleFailures;
    private final long startTimeMs;

    private int successes = 0;
    private int failures = 0;
    private final List<Throwable> exampleFailures = new ArrayList<>();
    private TerminationReason terminationReason = null;
    private String terminationDetails = null;
    private boolean forcedFailure = false;

    /**
     * Creates a new aggregator for the specified number of samples.
     *
     * @param totalSamples the total number of samples planned to execute
     * @param maxExampleFailures maximum number of example failures to capture
     */
    public SampleResultAggregator(int totalSamples, int maxExampleFailures) {
        this.totalSamples = totalSamples;
        this.maxExampleFailures = maxExampleFailures;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Creates a new aggregator with default max example failures of 5.
     *
     * @param totalSamples the total number of samples planned to execute
     */
    public SampleResultAggregator(int totalSamples) {
        this(totalSamples, 5);
    }

    /**
     * Records a successful sample execution.
     */
    public void recordSuccess() {
        successes++;
    }

    /**
     * Records a failed sample execution.
     *
     * @param cause the exception that caused the failure (may be null)
     */
    public void recordFailure(Throwable cause) {
        failures++;
        if (cause != null && exampleFailures.size() < maxExampleFailures) {
            exampleFailures.add(cause);
        }
    }

    /**
     * Returns the number of successful samples.
     *
     * @return success count
     */
    public int getSuccesses() {
        return successes;
    }

    /**
     * Returns the number of failed samples.
     *
     * @return failure count
     */
    public int getFailures() {
        return failures;
    }

    /**
     * Returns the total number of samples executed so far.
     *
     * @return samples executed (successes + failures)
     */
    public int getSamplesExecuted() {
        return successes + failures;
    }

    /**
     * Returns the total number of samples planned.
     *
     * @return total planned samples
     */
    public int getTotalSamples() {
        return totalSamples;
    }

    /**
     * Returns the observed pass rate as a value between 0.0 and 1.0.
     *
     * @return observed pass rate, or 0.0 if no samples executed
     */
    public double getObservedPassRate() {
        int executed = getSamplesExecuted();
        if (executed == 0) {
            return 0.0;
        }
        return (double) successes / executed;
    }

    /**
     * Returns the elapsed time since this aggregator was created.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Returns an unmodifiable list of example failure causes.
     *
     * @return list of captured failure exceptions
     */
    public List<Throwable> getExampleFailures() {
        return Collections.unmodifiableList(exampleFailures);
    }

    /**
     * Returns the number of remaining samples that have not yet executed.
     *
     * @return remaining sample count
     */
    public int getRemainingSamples() {
        return totalSamples - getSamplesExecuted();
    }

    /**
     * Marks this aggregator as terminated with the given reason.
     *
     * @param reason the reason for termination
     * @param details optional detailed explanation (may be null)
     */
    public void setTerminated(TerminationReason reason, String details) {
        this.terminationReason = reason;
        this.terminationDetails = details;
    }

    /**
     * Marks this aggregator as completed (all samples executed).
     */
    public void setCompleted() {
        this.terminationReason = TerminationReason.COMPLETED;
        this.terminationDetails = null;
    }

    /**
     * Returns the termination reason, or null if not yet terminated.
     *
     * @return termination reason
     */
    public Optional<TerminationReason> getTerminationReason() {
        return Optional.ofNullable(terminationReason);
    }

    /**
     * Returns additional details about the termination, or null if none.
     *
     * @return termination details
     */
    public String getTerminationDetails() {
        return terminationDetails;
    }

    /**
     * Returns true if this test was terminated early (before all samples completed).
     *
     * @return true if early termination occurred
     */
    public boolean wasTerminatedEarly() {
        return terminationReason != null && terminationReason.isEarlyTermination();
    }

    /**
     * Returns true if all samples have been executed or termination has occurred.
     *
     * @return true if test execution is complete
     */
    public boolean isComplete() {
        return terminationReason != null || getSamplesExecuted() >= totalSamples;
    }

    /**
     * Sets whether this test should be forced to fail regardless of pass rate.
     * 
     * <p>This flag is set when budget exhaustion occurs AND the configured
     * {@link org.javai.punit.api.BudgetExhaustedBehavior} is {@code FAIL}.
     * When this flag is set, the test fails even if the observed pass rate
     * meets the threshold.
     * 
     * <p><b>Note:</b> This flag does NOT indicate budget exhaustion in general.
     * Budget exhaustion with {@code EVALUATE_PARTIAL} behavior does not set this flag.
     * To check for budget exhaustion, use {@code getTerminationReason().isBudgetExhaustion()}.
     *
     * @param forced true to force failure regardless of pass rate
     */
    public void setForcedFailure(boolean forced) {
        this.forcedFailure = forced;
    }

    /**
     * Returns true if this test has been forced to fail regardless of pass rate.
     * 
     * <p>This returns true only when budget was exhausted AND the behavior was
     * {@code FAIL}. It does NOT indicate budget exhaustion in general.
     * To check for budget exhaustion, use {@code getTerminationReason().isBudgetExhaustion()}.
     *
     * @return true if the test was forced to fail regardless of pass rate
     */
    public boolean isForcedFailure() {
        return forcedFailure;
    }
}

