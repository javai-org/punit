package org.javai.punit.controls.pacing;

import org.javai.punit.api.Pacing;

/**
 * Computes optimal execution plan from pacing constraints.
 *
 * <p>This calculator takes raw pacing constraints (rate limits, concurrency,
 * explicit delays) and computes the effective execution plan that satisfies
 * all constraints while maximizing throughput.
 *
 * <h2>Computation Rules</h2>
 * <ul>
 *   <li><b>Effective delay</b>: The maximum of all implied delays from constraints</li>
 *   <li><b>Effective concurrency</b>: May be throttled if it would violate rate limits</li>
 *   <li><b>Estimated duration</b>: Computed from samples, delay, and concurrency</li>
 * </ul>
 *
 * @see Pacing
 * @see PacingConfiguration
 */
public class PacingCalculator {

    /**
     * Computes the execution plan for a given sample count and pacing annotation.
     *
     * @param samples the number of samples to execute
     * @param pacing the pacing annotation (may be null)
     * @return the computed pacing configuration
     */
    public PacingConfiguration compute(int samples, Pacing pacing) {
        return compute(samples, pacing, 0);
    }

    /**
     * Computes the execution plan for a given sample count and constraints.
     *
     * @param samples the number of samples to execute
     * @param pacing the pacing annotation (may be null)
     * @param estimatedLatencyMs estimated average latency per sample (0 = unknown)
     * @return the computed pacing configuration
     */
    public PacingConfiguration compute(int samples, Pacing pacing, long estimatedLatencyMs) {
        if (pacing == null) {
            return PacingConfiguration.noPacing();
        }

        return compute(
                samples,
                pacing.maxRequestsPerSecond(),
                pacing.maxRequestsPerMinute(),
                pacing.maxRequestsPerHour(),
                pacing.maxConcurrentRequests(),
                pacing.minMsPerSample(),
                estimatedLatencyMs
        );
    }

    /**
     * Computes the execution plan from explicit constraint values.
     *
     * @param samples the number of samples to execute
     * @param maxRps maximum requests per second (0 = unlimited)
     * @param maxRpm maximum requests per minute (0 = unlimited)
     * @param maxRph maximum requests per hour (0 = unlimited)
     * @param maxConcurrent maximum concurrent requests (0 or 1 = sequential)
     * @param minMsPerSample explicit minimum delay (0 = none)
     * @param estimatedLatencyMs estimated average latency per sample (0 = unknown)
     * @return the computed pacing configuration
     */
    public PacingConfiguration compute(
            int samples,
            double maxRps,
            double maxRpm,
            double maxRph,
            int maxConcurrent,
            long minMsPerSample,
            long estimatedLatencyMs) {

        // Compute effective minimum delay from all rate constraints
        long effectiveMinDelayMs = computeEffectiveDelay(maxRps, maxRpm, maxRph, minMsPerSample);

        // Compute effective concurrency (may be throttled by rate limits)
        int effectiveConcurrency = computeEffectiveConcurrency(
                maxConcurrent,
                effectiveMinDelayMs,
                maxRps,
                maxRpm,
                maxRph,
                estimatedLatencyMs
        );

        // Compute effective RPS
        double effectiveRps = computeEffectiveRps(effectiveMinDelayMs, effectiveConcurrency);

        // Clamp to rate limits
        effectiveRps = clampToRateLimits(effectiveRps, maxRps, maxRpm, maxRph);

        // Compute estimated duration
        long estimatedDurationMs = computeEstimatedDuration(samples, effectiveRps, estimatedLatencyMs);

        return new PacingConfiguration(
                maxRps,
                maxRpm,
                maxRph,
                maxConcurrent,
                minMsPerSample,
                effectiveMinDelayMs,
                effectiveConcurrency,
                estimatedDurationMs,
                effectiveRps
        );
    }

    /**
     * Computes the effective minimum delay from all constraints.
     * The most restrictive constraint (highest delay) wins.
     */
    long computeEffectiveDelay(double maxRps, double maxRpm, double maxRph, long minMsPerSample) {
        long delay = minMsPerSample;

        if (maxRps > 0) {
            delay = Math.max(delay, (long) Math.ceil(1000.0 / maxRps));
        }
        if (maxRpm > 0) {
            delay = Math.max(delay, (long) Math.ceil(60000.0 / maxRpm));
        }
        if (maxRph > 0) {
            delay = Math.max(delay, (long) Math.ceil(3600000.0 / maxRph));
        }

        return delay;
    }

    /**
     * Computes the effective concurrency level.
     * 
     * <p>If no rate limits are specified, returns the requested concurrency.
     * If rate limits would be violated by the requested concurrency, the
     * concurrency is throttled.
     */
    int computeEffectiveConcurrency(
            int maxConcurrent,
            long effectiveDelayMs,
            double maxRps,
            double maxRpm,
            double maxRph,
            long estimatedLatencyMs) {

        // Default to sequential if not specified
        if (maxConcurrent <= 1) {
            return 1;
        }

        // If no rate limits, use requested concurrency
        if (maxRps <= 0 && maxRpm <= 0 && maxRph <= 0) {
            return maxConcurrent;
        }

        // If we don't know the latency, we can't compute sustainable concurrency
        // so just use the requested value
        if (estimatedLatencyMs <= 0) {
            return maxConcurrent;
        }

        // Calculate max sustainable concurrency given rate limits
        // Each worker can issue requests every (latency) ms
        // With N workers, aggregate rate is N / latency * 1000 RPS
        double maxRpsLimit = Double.MAX_VALUE;
        if (maxRps > 0) {
            maxRpsLimit = Math.min(maxRpsLimit, maxRps);
        }
        if (maxRpm > 0) {
            maxRpsLimit = Math.min(maxRpsLimit, maxRpm / 60.0);
        }
        if (maxRph > 0) {
            maxRpsLimit = Math.min(maxRpsLimit, maxRph / 3600.0);
        }

        // Max concurrent = maxRpsLimit * latency / 1000
        int sustainableConcurrency = (int) (maxRpsLimit * estimatedLatencyMs / 1000);
        sustainableConcurrency = Math.max(1, sustainableConcurrency);

        return Math.min(maxConcurrent, sustainableConcurrency);
    }

    /**
     * Computes the effective requests per second based on delay and concurrency.
     */
    double computeEffectiveRps(long effectiveDelayMs, int concurrency) {
        if (effectiveDelayMs <= 0) {
            return Double.MAX_VALUE;
        }
        // With staggered workers, aggregate RPS = concurrency / (delay in seconds)
        return (double) concurrency * 1000.0 / effectiveDelayMs;
    }

    /**
     * Clamps the effective RPS to respect rate limits.
     */
    double clampToRateLimits(double rps, double maxRps, double maxRpm, double maxRph) {
        if (maxRps > 0) {
            rps = Math.min(rps, maxRps);
        }
        if (maxRpm > 0) {
            rps = Math.min(rps, maxRpm / 60.0);
        }
        if (maxRph > 0) {
            rps = Math.min(rps, maxRph / 3600.0);
        }
        return rps;
    }

    /**
     * Computes estimated duration based on samples and effective RPS.
     */
    long computeEstimatedDuration(int samples, double effectiveRps, long estimatedLatencyMs) {
        if (samples <= 0) {
            return 0;
        }
        if (effectiveRps <= 0 || effectiveRps == Double.MAX_VALUE) {
            // No rate limiting - estimate based on latency if known
            if (estimatedLatencyMs > 0) {
                return samples * estimatedLatencyMs;
            }
            return 0;
        }
        // Duration = samples / RPS (converted to ms)
        return (long) (samples / effectiveRps * 1000);
    }
}

