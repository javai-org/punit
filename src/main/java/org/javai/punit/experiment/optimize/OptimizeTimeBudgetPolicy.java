package org.javai.punit.experiment.optimize;

import java.time.Duration;
import java.util.Optional;

/**
 * Terminates when time budget is exhausted.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Stop after 10 minutes
 * OptimizeTerminationPolicy policy = new OptimizeTimeBudgetPolicy(Duration.ofMinutes(10));
 * }</pre>
 */
public final class OptimizeTimeBudgetPolicy implements OptimizeTerminationPolicy {

    private final Duration maxDuration;

    /**
     * Creates a policy that terminates after maxDuration.
     *
     * @param maxDuration the maximum duration
     * @throws IllegalArgumentException if maxDuration is null or non-positive
     */
    public OptimizeTimeBudgetPolicy(Duration maxDuration) {
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
     * @return a new OptimizeTimeBudgetPolicy
     */
    public static OptimizeTimeBudgetPolicy ofMillis(long maxDurationMs) {
        return new OptimizeTimeBudgetPolicy(Duration.ofMillis(maxDurationMs));
    }

    @Override
    public Optional<OptimizeTerminationReason> shouldTerminate(OptimizeHistory history) {
        Duration elapsed = history.totalDuration();
        if (elapsed.compareTo(maxDuration) >= 0) {
            return Optional.of(OptimizeTerminationReason.timeBudgetExhausted(maxDuration.toMillis()));
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
