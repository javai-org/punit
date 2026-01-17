package org.javai.punit.controls.pacing;

import java.time.Instant;
import org.javai.punit.reporting.PUnitReporter;

/**
 * Generates pre-flight reports for pacing-enabled test execution.
 *
 * <p>The pre-flight report shows:
 * <ul>
 *   <li>Configured pacing constraints</li>
 *   <li>Computed execution plan</li>
 *   <li>Estimated duration and completion time</li>
 *   <li>Feasibility warnings if constraints conflict</li>
 * </ul>
 *
 * <p>All output is delegated to {@link PUnitReporter} for consistent formatting.
 */
public class PacingReporter {

    private final PUnitReporter reporter;

    /**
     * Creates a reporter using the default PUnitReporter.
     */
    public PacingReporter() {
        this(new PUnitReporter());
    }

    /**
     * Creates a reporter using the specified PUnitReporter.
     *
     * @param reporter the reporter to use for output
     */
    public PacingReporter(PUnitReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Prints a pre-flight report for the given test and pacing configuration.
     *
     * @param testName the name of the test or experiment
     * @param samples the number of samples to execute
     * @param pacing the pacing configuration
     * @param startTime the execution start time
     */
    public void printPreFlightReport(String testName, int samples, PacingConfiguration pacing, Instant startTime) {
        if (!pacing.hasPacing()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(testName).append("\n");
        sb.append("Samples requested: ").append(samples).append("\n\n");

        sb.append("Pacing constraints:\n");
        if (pacing.maxRequestsPerMinute() > 0) {
            sb.append("  • Max requests/min: ").append(formatNumber(pacing.maxRequestsPerMinute())).append(" RPM\n");
        }
        if (pacing.maxRequestsPerSecond() > 0) {
            sb.append("  • Max requests/sec: ").append(formatNumber(pacing.maxRequestsPerSecond())).append(" RPS\n");
        }
        if (pacing.maxRequestsPerHour() > 0) {
            sb.append("  • Max requests/hour: ").append(formatNumber(pacing.maxRequestsPerHour())).append(" RPH\n");
        }
        if (pacing.maxConcurrentRequests() > 1) {
            sb.append("  • Max concurrent: ").append(pacing.maxConcurrentRequests()).append("\n");
        }
        if (pacing.minMsPerSample() > 0) {
            sb.append("  • Min delay/sample: ").append(pacing.minMsPerSample()).append("ms (explicit)\n");
        } else if (pacing.effectiveMinDelayMs() > 0) {
            sb.append("  • Min delay/sample: ").append(pacing.effectiveMinDelayMs()).append("ms (").append(pacing.delaySource()).append(")\n");
        }

        sb.append("\nComputed execution plan:\n");
        if (pacing.isConcurrent()) {
            sb.append("  • Concurrency: ").append(pacing.effectiveConcurrency()).append(" workers\n");
        } else {
            sb.append("  • Concurrency: sequential\n");
        }
        if (pacing.effectiveMinDelayMs() > 0) {
            if (pacing.isConcurrent()) {
                long perWorkerDelay = pacing.effectiveMinDelayMs() * pacing.effectiveConcurrency();
                sb.append("  • Inter-request delay: ").append(perWorkerDelay).append("ms per worker (staggered)\n");
            } else {
                sb.append("  • Inter-request delay: ").append(pacing.effectiveMinDelayMs()).append("ms\n");
            }
        }
        sb.append("  • Effective throughput: ").append(pacing.formattedThroughput()).append("\n");
        sb.append("  • Estimated duration: ").append(pacing.formattedDuration()).append("\n");

        Instant completionTime = pacing.estimatedCompletionTime(startTime);
        sb.append("  • Estimated completion: ").append(pacing.formatTime(completionTime)).append("\n");

        sb.append("\nStarted: ").append(pacing.formatTime(startTime)).append("\n");
        sb.append("Proceeding with execution...");

        reporter.reportInfo("EXECUTION PLAN", sb.toString());
    }

    /**
     * Prints a feasibility warning if pacing constraints conflict with budget constraints.
     *
     * @param pacing the pacing configuration
     * @param timeBudgetMs the configured time budget (0 = unlimited)
     * @param samples the number of samples
     */
    public void printFeasibilityWarning(PacingConfiguration pacing, long timeBudgetMs, int samples) {
        if (!pacing.hasPacing() || timeBudgetMs <= 0) {
            return;
        }

        if (pacing.estimatedDurationMs() > timeBudgetMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("• ").append(samples).append(" samples at current pacing would take ~")
              .append(pacing.formattedDuration()).append("\n");
            sb.append("• Time budget is ").append(formatDuration(timeBudgetMs))
              .append(" (timeBudgetMs = ").append(timeBudgetMs).append(")\n\n");
            sb.append("Options:\n");

            long estimatedPerSampleMs = pacing.estimatedDurationMs() / samples;
            if (estimatedPerSampleMs > 0) {
                int maxSamples = (int) (timeBudgetMs / estimatedPerSampleMs);
                sb.append("  1. Reduce sample count to ~").append(maxSamples).append("\n");
            }
            sb.append("  2. Increase time budget to ").append(formatDuration(pacing.estimatedDurationMs() + 10000)).append("\n");
            sb.append("  3. Relax pacing constraints (increase rate limits)");

            reporter.reportWarn("PACING CONFLICT", sb.toString());
        }
    }

    /**
     * Formats a number, removing unnecessary decimal places.
     */
    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        }
        return String.format("%.1f", value);
    }

    /**
     * Formats a duration in milliseconds as a human-readable string.
     */
    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
