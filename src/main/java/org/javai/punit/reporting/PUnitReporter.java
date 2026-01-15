package org.javai.punit.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Formats and emits PUnit reports using a consistent visual header/footer.
 *
 * <p>Each report includes a title on the left and "PUnit" branding on the right:
 * <pre>
 * ═ VERDICT: PASS ════════════════════════════════════════════════════ PUnit ═
 * shouldReturnValidJson
 * Observed pass rate: 95.0% (95/100) >= min pass rate: 90.0%
 * Elapsed: 1234ms
 * ══════════════════════════════════════════════════════════════════════════════
 * </pre>
 */
public final class PUnitReporter {

    private static final Logger logger = LogManager.getLogger(PUnitReporter.class);
    private static final int DEFAULT_WIDTH = 78;
    private static final String SUFFIX = " PUnit ═";

    private final int width;

    public PUnitReporter() {
        this(DEFAULT_WIDTH);
    }

    public PUnitReporter(int width) {
        this.width = Math.max(24, width);
    }

    /**
     * Logs a report at INFO level.
     *
     * @param title the report title (e.g., "VERDICT: PASS", "BASELINE SELECTED")
     * @param body the report body content
     */
    public void reportInfo(String title, String body) {
        logger.info(format(title, body));
    }

    /**
     * Logs a report at WARN level.
     *
     * @param title the report title (e.g., "BASELINE EXPIRED")
     * @param body the report body content
     */
    public void reportWarn(String title, String body) {
        logger.warn(format(title, body));
    }

    /**
     * Logs a report at ERROR level.
     *
     * @param title the report title
     * @param body the report body content
     */
    public void reportError(String title, String body) {
        logger.error(format(title, body));
    }

    /**
     * Returns the header divider with title on left and "PUnit" on right.
     *
     * <p>Format: {@code ═ {TITLE} ════════════════════════════════════ PUnit ═}
     *
     * <p>If the title is too long, it is truncated with "..." and the " PUnit ═"
     * suffix is sacrificed if necessary to stay within the configured width.
     *
     * @param title the title to include in the header
     * @return the formatted header divider
     */
    public String headerDivider(String title) {
        String safeTitle = (title == null ? "" : title);
        String prefix = "═ " + safeTitle + " ";
        int fillerLength = width - prefix.length() - SUFFIX.length();
        
        if (fillerLength >= 1) {
            // Normal case: title fits with suffix and filler
            return prefix + "═".repeat(fillerLength) + SUFFIX;
        }
        
        // Title is too long - need to truncate
        // Calculate max title length that allows for: "═ " + title + "... ═"
        // (sacrificing the " PUnit ═" suffix)
        int maxTitleLength = width - "═ ".length() - "... ═".length();
        
        if (safeTitle.length() <= maxTitleLength) {
            // Title fits without suffix, just drop the suffix
            int fillerWithoutSuffix = width - prefix.length() - 1; // -1 for trailing ═
            if (fillerWithoutSuffix >= 1) {
                return prefix + "═".repeat(fillerWithoutSuffix) + "═";
            }
            return prefix.trim() + " ═";
        }
        
        // Truncate title with ellipsis
        String truncatedTitle = safeTitle.substring(0, maxTitleLength) + "...";
        return "═ " + truncatedTitle + " ═";
    }

    /**
     * Returns the footer divider (plain line).
     */
    public String footerDivider() {
        return "═".repeat(width);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Formatting utilities for consistent label-value alignment
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default label width for aligned label-value formatting.
     *
     * <p>Used by most reports (CONFIGURATION, BASELINE, VERDICT).
     */
    public static final int DEFAULT_LABEL_WIDTH = 18;

    /**
     * Label width for detailed statistical analysis sections.
     *
     * <p>Used by STATISTICAL ANALYSIS reports where labels like
     * "Sample size (n):" need more space.
     */
    public static final int STATS_LABEL_WIDTH = 21;

    /**
     * Formats a label-value line with consistent alignment.
     *
     * <p>The label is left-aligned and padded to 18 characters by default,
     * ensuring values line up across multiple lines:
     * <pre>
     * Mode:              SPEC-DRIVEN
     * Spec:              ShoppingUseCase
     * Threshold:         95.0% (derived from baseline)
     * </pre>
     *
     * @param label the label (e.g., "Mode:", "Threshold:")
     * @param value the value to display
     * @return formatted line without trailing newline
     */
    public static String labelValue(String label, String value) {
        return labelValue(label, value, DEFAULT_LABEL_WIDTH);
    }

    /**
     * Formats a label-value line with custom label width.
     *
     * <p>The value starts immediately after the label padding, so the label width
     * should include any desired spacing. For example, if you want values to
     * align at column 20, use labelWidth=20.
     *
     * @param label the label (e.g., "Mode:", "Threshold:")
     * @param value the value to display
     * @param labelWidth the minimum width for the label column (including trailing space)
     * @return formatted line without trailing newline
     */
    public static String labelValue(String label, String value, int labelWidth) {
        return String.format("%-" + labelWidth + "s%s", label, value);
    }

    /**
     * Formats a label-value line and appends a newline.
     *
     * <p>Convenience method for building multi-line reports:
     * <pre>
     * StringBuilder sb = new StringBuilder();
     * sb.append(PUnitReporter.labelValueLn("Mode:", "SPEC-DRIVEN"));
     * sb.append(PUnitReporter.labelValueLn("Spec:", specId));
     * </pre>
     *
     * @param label the label
     * @param value the value
     * @return formatted line with trailing newline
     */
    public static String labelValueLn(String label, String value) {
        return labelValue(label, value) + "\n";
    }

    /**
     * Formats a label-value line with custom width and appends a newline.
     *
     * @param label the label
     * @param value the value
     * @param labelWidth the minimum width for the label column
     * @return formatted line with trailing newline
     */
    public static String labelValueLn(String label, String value, int labelWidth) {
        return labelValue(label, value, labelWidth) + "\n";
    }

    private String format(String title, String body) {
        String trimmed = body == null ? "" : body.trim();
        return headerDivider(title) + "\n" + trimmed + "\n" + footerDivider();
    }
}
