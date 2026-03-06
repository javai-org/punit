package org.javai.punit.controls.pacing;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.javai.punit.reporting.DurationFormat;
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
        sb.append(testName).append("\n\n");
        sb.append(PUnitReporter.labelValueLn("Samples:", String.valueOf(samples)));

        sb.append("\nPACING CONSTRAINTS\n");
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
            sb.append("  • Min delay/sample: ").append(pacing.effectiveMinDelayMs()).append("ms (").append(delaySource(pacing)).append(")\n");
        }

        sb.append("\nCOMPUTED PLAN\n");
        if (pacing.isConcurrent()) {
            sb.append("  ").append(PUnitReporter.labelValueLn("Concurrency:", pacing.effectiveConcurrency() + " workers"));
        } else {
            sb.append("  ").append(PUnitReporter.labelValueLn("Concurrency:", "sequential"));
        }
        if (pacing.effectiveMinDelayMs() > 0) {
            if (pacing.isConcurrent()) {
                long perWorkerDelay = pacing.effectiveMinDelayMs() * pacing.effectiveConcurrency();
                sb.append("  ").append(PUnitReporter.labelValueLn("Inter-request delay:", perWorkerDelay + "ms per worker (staggered)"));
            } else {
                sb.append("  ").append(PUnitReporter.labelValueLn("Inter-request delay:", pacing.effectiveMinDelayMs() + "ms"));
            }
        }
        sb.append("  ").append(PUnitReporter.labelValueLn("Effective throughput:", formattedThroughput(pacing)));
        sb.append("  ").append(PUnitReporter.labelValueLn("Estimated duration:", formattedDuration(pacing)));

        Instant completionTime = pacing.estimatedCompletionTime(startTime);
        sb.append("  ").append(PUnitReporter.labelValueLn("Estimated completion:", formatTime(completionTime)));

        sb.append("\n").append(PUnitReporter.labelValue("Started:", formatTime(startTime)));

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
            sb.append(samples).append(" samples at current pacing would take ~")
              .append(formattedDuration(pacing))
              .append(", but the time budget is ")
              .append(DurationFormat.execution(timeBudgetMs))
              .append(" (timeBudgetMs = ").append(timeBudgetMs).append(").\n");

            sb.append("\nREMEDIATION\n");

            int optionNumber = 1;
            long estimatedPerSampleMs = pacing.estimatedDurationMs() / samples;
            if (estimatedPerSampleMs > 0) {
                int maxSamples = (int) (timeBudgetMs / estimatedPerSampleMs);
                sb.append("  ").append(optionNumber++).append(". Reduce sample count to ~").append(maxSamples).append("\n");
            }
            sb.append("  ").append(optionNumber++).append(". Increase time budget to ").append(DurationFormat.execution(pacing.estimatedDurationMs() + 10000)).append("\n");
            sb.append("  ").append(optionNumber).append(". Relax pacing constraints (increase rate limits)");

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

    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATTING METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Formats the estimated duration as a human-readable string (e.g., "3m 20s").
     *
     * @param pacing the pacing configuration
     * @return the formatted duration string
     */
    public static String formattedDuration(PacingConfiguration pacing) {
        if (pacing.estimatedDurationMs() <= 0) {
            return "< 1s";
        }
        long totalSeconds = pacing.estimatedDurationMs() / 1000;
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
    public static String formatTime(Instant instant) {
        LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
        return TIME_FORMATTER.format(localTime);
    }

    /**
     * Formats the effective throughput as a human-readable string.
     *
     * @param pacing the pacing configuration
     * @return the formatted throughput (e.g., "60 samples/min")
     */
    public static String formattedThroughput(PacingConfiguration pacing) {
        if (pacing.effectiveRps() == Double.MAX_VALUE || pacing.effectiveRps() <= 0) {
            return "unlimited";
        }
        double rpm = pacing.effectiveRps() * 60;
        if (rpm >= 1) {
            return String.format("%.0f samples/min", rpm);
        } else {
            return String.format("%.2f samples/min", rpm);
        }
    }

    /**
     * Returns a description of the derived delay source.
     *
     * @param pacing the pacing configuration
     * @return description of why the effective delay was chosen
     */
    public static String delaySource(PacingConfiguration pacing) {
        if (pacing.effectiveMinDelayMs() <= 0) {
            return "none";
        }
        long fromRps = pacing.maxRequestsPerSecond() > 0 ? (long) (1000 / pacing.maxRequestsPerSecond()) : 0;
        long fromRpm = pacing.maxRequestsPerMinute() > 0 ? (long) (60000 / pacing.maxRequestsPerMinute()) : 0;
        long fromRph = pacing.maxRequestsPerHour() > 0 ? (long) (3600000 / pacing.maxRequestsPerHour()) : 0;

        if (pacing.minMsPerSample() > 0 && pacing.minMsPerSample() >= pacing.effectiveMinDelayMs()) {
            return "explicit minMsPerSample";
        } else if (fromRpm > 0 && fromRpm >= pacing.effectiveMinDelayMs()) {
            return String.format("derived from %,.0f RPM", pacing.maxRequestsPerMinute());
        } else if (fromRps > 0 && fromRps >= pacing.effectiveMinDelayMs()) {
            return String.format("derived from %,.1f RPS", pacing.maxRequestsPerSecond());
        } else if (fromRph > 0 && fromRph >= pacing.effectiveMinDelayMs()) {
            return String.format("derived from %,.0f RPH", pacing.maxRequestsPerHour());
        }
        return String.format("%dms", pacing.effectiveMinDelayMs());
    }
}
