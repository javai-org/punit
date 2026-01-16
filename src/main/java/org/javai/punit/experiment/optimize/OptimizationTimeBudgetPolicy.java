package org.javai.punit.experiment.optimize;

import java.time.Duration;
import java.util.Optional;

/**
 * Terminates when time budget is exhausted.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Stop after 10 minutes
 * OptimizationTerminationPolicy policy = new OptimizationTimeBudgetPolicy(Duration.ofMinutes(10));
 * }</pre>
 */
public final class OptimizationTimeBudgetPolicy implements OptimizationTerminationPolicy {

    private final Duration maxDuration;

    /**
     * Creates a policy that terminates after maxDuration.
     *
     * @param maxDuration the maximum duration
     * @throws IllegalArgumentException if maxDuration is null or non-positive
     */
    public OptimizationTimeBudgetPolicy(Duration maxDuration) {
        if (maxDuration == null) {
            throw new IllegalArgumentException("maxDuration must not be null");
        }
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        this.maxDuration = maxDuration;
    }

    /**
     * Creates a policy that terminates after maxDurationMs milliseconds.
     *
     * @param maxDurationMs the maximum duration in milliseconds
     * @return a new OptimizationTimeBudgetPolicy
     */
    public static OptimizationTimeBudgetPolicy ofMillis(long maxDurationMs) {
        return new OptimizationTimeBudgetPolicy(Duration.ofMillis(maxDurationMs));
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        Duration elapsed = history.totalDuration();
        if (elapsed.compareTo(maxDuration) >= 0) {
            return Optional.of(OptimizationTerminationReason.timeBudgetExhausted(maxDuration.toMillis()));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Time budget: " + formatDuration(maxDuration);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            return (seconds / 3600) + "h";
        }
    }
}
