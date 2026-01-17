package org.javai.punit.controls.pacing;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Resolved pacing configuration with computed execution plan.
 *
 * <p>This record holds both the raw pacing constraints (as declared by the developer)
 * and the computed execution plan (derived by {@link PacingCalculator}).
 *
 * <h2>Computed Values</h2>
 * <ul>
 *   <li>{@code effectiveMinDelayMs} — the actual delay between samples after considering all constraints</li>
 *   <li>{@code effectiveConcurrency} — the actual concurrency level (may be throttled)</li>
 *   <li>{@code estimatedDurationMs} — how long execution will take</li>
 *   <li>{@code effectiveRps} — the effective requests per second</li>
 * </ul>
 */
public record PacingConfiguration(
        // Raw constraints
        double maxRequestsPerSecond,
        double maxRequestsPerMinute,
        double maxRequestsPerHour,
        int maxConcurrentRequests,
        long minMsPerSample,

        // Computed execution plan
        long effectiveMinDelayMs,
        int effectiveConcurrency,
        long estimatedDurationMs,
        double effectiveRps
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Creates a "no pacing" configuration for when no constraints are specified.
     *
     * @return a configuration with no pacing constraints
     */
    public static PacingConfiguration noPacing() {
        return new PacingConfiguration(0, 0, 0, 0, 0, 0, 1, 0, Double.MAX_VALUE);
    }

    /**
     * Returns true if any pacing constraint is configured.
     */
    public boolean hasPacing() {
        return maxRequestsPerSecond > 0
                || maxRequestsPerMinute > 0
                || maxRequestsPerHour > 0
                || maxConcurrentRequests > 1
                || minMsPerSample > 0;
    }

    /**
     * Returns true if this configuration enables concurrent execution.
     */
    public boolean isConcurrent() {
        return effectiveConcurrency > 1;
    }

    /**
     * Computes the estimated completion time based on start time and duration.
     *
     * @param startTime the execution start time
     * @return the estimated completion time
     */
    public Instant estimatedCompletionTime(Instant startTime) {
        return startTime.plusMillis(estimatedDurationMs);
    }

    /**
     * Formats the estimated duration as a human-readable string (e.g., "3m 20s").
     */
    public String formattedDuration() {
        if (estimatedDurationMs <= 0) {
            return "< 1s";
        }
        long totalSeconds = estimatedDurationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Formats a time instant as HH:mm:ss in the system default timezone.
     *
     * @param instant the instant to format
     * @return the formatted time string
     */
    public String formatTime(Instant instant) {
        LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
        return TIME_FORMATTER.format(localTime);
    }

    /**
     * Formats the effective throughput as a human-readable string.
     *
     * @return the formatted throughput (e.g., "60 samples/min")
     */
    public String formattedThroughput() {
        if (effectiveRps == Double.MAX_VALUE || effectiveRps <= 0) {
            return "unlimited";
        }
        double rpm = effectiveRps * 60;
        if (rpm >= 1) {
            return String.format("%.0f samples/min", rpm);
        } else {
            return String.format("%.2f samples/min", rpm);
        }
    }

    /**
     * Returns a description of the derived delay source.
     *
     * @return description of why the effective delay was chosen
     */
    public String delaySource() {
        if (effectiveMinDelayMs <= 0) {
            return "none";
        }
        // Determine which constraint drove the delay
        long fromRps = maxRequestsPerSecond > 0 ? (long) (1000 / maxRequestsPerSecond) : 0;
        long fromRpm = maxRequestsPerMinute > 0 ? (long) (60000 / maxRequestsPerMinute) : 0;
        long fromRph = maxRequestsPerHour > 0 ? (long) (3600000 / maxRequestsPerHour) : 0;

        if (minMsPerSample > 0 && minMsPerSample >= effectiveMinDelayMs) {
            return "explicit minMsPerSample";
        } else if (fromRpm > 0 && fromRpm >= effectiveMinDelayMs) {
            return String.format("derived from %,.0f RPM", maxRequestsPerMinute);
        } else if (fromRps > 0 && fromRps >= effectiveMinDelayMs) {
            return String.format("derived from %,.1f RPS", maxRequestsPerSecond);
        } else if (fromRph > 0 && fromRph >= effectiveMinDelayMs) {
            return String.format("derived from %,.0f RPH", maxRequestsPerHour);
        }
        return String.format("%dms", effectiveMinDelayMs);
    }
}

