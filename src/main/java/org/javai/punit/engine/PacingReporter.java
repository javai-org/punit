package org.javai.punit.engine;

import java.io.PrintStream;
import java.time.Instant;

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
 */
public class PacingReporter {

    private static final String BOX_TOP = "╔══════════════════════════════════════════════════════════════════╗";
    private static final String BOX_DIVIDER = "╠══════════════════════════════════════════════════════════════════╣";
    private static final String BOX_BOTTOM = "╚══════════════════════════════════════════════════════════════════╝";
    private static final int BOX_WIDTH = 66; // Inner width (excluding borders)

    private final PrintStream out;

    /**
     * Creates a reporter that writes to System.out.
     */
    public PacingReporter() {
        this(System.out);
    }

    /**
     * Creates a reporter that writes to the specified stream.
     *
     * @param out the output stream
     */
    public PacingReporter(PrintStream out) {
        this.out = out;
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
            // No pacing configured - no report needed
            return;
        }

        out.println();
        out.println(BOX_TOP);
        printLine("PUnit Test: " + truncate(testName, BOX_WIDTH - 12));
        out.println(BOX_DIVIDER);

        // Samples
        printLine("Samples requested:     " + samples);

        // Pacing constraints
        printLine("Pacing constraints:");
        if (pacing.maxRequestsPerMinute() > 0) {
            printLine("  • Max requests/min:  " + formatNumber(pacing.maxRequestsPerMinute()) + " RPM");
        }
        if (pacing.maxRequestsPerSecond() > 0) {
            printLine("  • Max requests/sec:  " + formatNumber(pacing.maxRequestsPerSecond()) + " RPS");
        }
        if (pacing.maxRequestsPerHour() > 0) {
            printLine("  • Max requests/hour: " + formatNumber(pacing.maxRequestsPerHour()) + " RPH");
        }
        if (pacing.maxConcurrentRequests() > 1) {
            printLine("  • Max concurrent:    " + pacing.maxConcurrentRequests());
        }
        if (pacing.minMsPerSample() > 0) {
            printLine("  • Min delay/sample:  " + pacing.minMsPerSample() + "ms (explicit)");
        } else if (pacing.effectiveMinDelayMs() > 0) {
            printLine("  • Min delay/sample:  " + pacing.effectiveMinDelayMs() + "ms (" + pacing.delaySource() + ")");
        }

        out.println(BOX_DIVIDER);

        // Computed execution plan
        printLine("Computed execution plan:");
        if (pacing.isConcurrent()) {
            printLine("  • Concurrency:         " + pacing.effectiveConcurrency() + " workers");
        } else {
            printLine("  • Concurrency:         sequential");
        }
        if (pacing.effectiveMinDelayMs() > 0) {
            if (pacing.isConcurrent()) {
                long perWorkerDelay = pacing.effectiveMinDelayMs() * pacing.effectiveConcurrency();
                printLine("  • Inter-request delay: " + perWorkerDelay + "ms per worker (staggered)");
            } else {
                printLine("  • Inter-request delay: " + pacing.effectiveMinDelayMs() + "ms");
            }
        }
        printLine("  • Effective throughput: " + pacing.formattedThroughput());
        printLine("  • Estimated duration:  " + pacing.formattedDuration());

        Instant completionTime = pacing.estimatedCompletionTime(startTime);
        printLine("  • Estimated completion: " + pacing.formatTime(completionTime));

        out.println(BOX_DIVIDER);
        printLine("Started: " + pacing.formatTime(startTime));
        printLine("Proceeding with execution...");
        out.println(BOX_BOTTOM);
        out.println();
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
            out.println();
            out.println("⚠ WARNING: Pacing conflict detected");
            out.printf("  • %d samples at current pacing would take ~%s%n",
                    samples, pacing.formattedDuration());
            out.printf("  • Time budget is %s (timeBudgetMs = %d)%n",
                    formatDuration(timeBudgetMs), timeBudgetMs);
            out.println("  • Options:");

            // Calculate how many samples would fit
            long estimatedPerSampleMs = pacing.estimatedDurationMs() / samples;
            if (estimatedPerSampleMs > 0) {
                int maxSamples = (int) (timeBudgetMs / estimatedPerSampleMs);
                out.printf("    1. Reduce sample count to ~%d%n", maxSamples);
            }
            out.printf("    2. Increase time budget to %s%n",
                    formatDuration(pacing.estimatedDurationMs() + 10000)); // Add 10s buffer
            out.println("    3. Relax pacing constraints (increase rate limits)");
            out.println();
        }
    }

    /**
     * Prints a line within the box, padded appropriately.
     */
    private void printLine(String content) {
        String truncated = truncate(content, BOX_WIDTH);
        out.printf("║ %-" + BOX_WIDTH + "s ║%n", truncated);
    }

    /**
     * Truncates a string to the specified length.
     */
    private String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
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

