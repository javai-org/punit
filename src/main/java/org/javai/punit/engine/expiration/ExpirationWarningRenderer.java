package org.javai.punit.engine.expiration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.javai.punit.model.ExpirationPolicy;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Renders expiration warnings for display in test output.
 *
 * <p>Produces formatted warning messages based on expiration status:
 * <ul>
 *   <li><strong>Expired</strong>: Prominent warning with remediation guidance</li>
 *   <li><strong>Expiring imminently</strong>: Warning format with urgency</li>
 *   <li><strong>Expiring soon</strong>: Informational format</li>
 * </ul>
 *
 * <p>All output is designed to be human-readable and immediately actionable.
 */
public final class ExpirationWarningRenderer {

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private ExpirationWarningRenderer() {
        // Utility class
    }

    /**
     * Result of rendering an expiration warning.
     *
     * @param title the warning title for the PUnit header
     * @param body the warning body content
     */
    public record WarningContent(String title, String body) {
        public boolean isEmpty() {
            return title == null || title.isEmpty();
        }
    }

    /**
     * Renders an expiration warning for the given status.
     *
     * @param spec the execution specification containing the expiration policy
     * @param status the expiration status
     * @return the rendered warning content, or empty content if no warning is needed
     */
    public static WarningContent renderWarning(ExecutionSpecification spec, ExpirationStatus status) {
        if (status == null || !status.requiresWarning()) {
            return new WarningContent("", "");
        }

        return switch (status) {
            case ExpirationStatus.Expired expired -> 
                renderExpired(spec.getExpirationPolicy(), expired);
            case ExpirationStatus.ExpiringImminently imminent -> 
                renderExpiringImminently(spec.getExpirationPolicy(), imminent);
            case ExpirationStatus.ExpiringSoon soon -> 
                renderExpiringSoon(spec.getExpirationPolicy(), soon);
            default -> new WarningContent("", "");
        };
    }

    /**
     * Renders an expiration warning for the given status.
     *
     * @param spec the execution specification containing the expiration policy
     * @param status the expiration status
     * @return the rendered warning, or empty string if no warning is needed
     * @deprecated Use {@link #renderWarning(ExecutionSpecification, ExpirationStatus)} instead
     */
    @Deprecated
    public static String render(ExecutionSpecification spec, ExpirationStatus status) {
        WarningContent content = renderWarning(spec, status);
        if (content.isEmpty()) {
            return "";
        }
        return content.title() + "\n" + content.body();
    }

    /**
     * Label width for expiration warnings (matches original formatting).
     */
    private static final int EXPIRATION_LABEL_WIDTH = 20;

    /**
     * Renders a prominent warning for an expired baseline.
     */
    private static WarningContent renderExpired(ExpirationPolicy policy, ExpirationStatus.Expired status) {
        StringBuilder sb = new StringBuilder();
        sb.append("The baseline used for statistical inference has expired.\n\n");
        sb.append(PUnitReporter.labelValueLn("Baseline created:", formatInstant(policy.baselineEndTime()), EXPIRATION_LABEL_WIDTH));
        sb.append(PUnitReporter.labelValueLn("Validity period:", policy.expiresInDays() + " days", EXPIRATION_LABEL_WIDTH));
        sb.append(PUnitReporter.labelValueLn("Expiration date:", formatInstant(policy.expirationTime().orElse(null)), EXPIRATION_LABEL_WIDTH));
        sb.append(PUnitReporter.labelValueLn("Expired:", formatDuration(status.expiredAgo()) + " ago", EXPIRATION_LABEL_WIDTH));
        sb.append("\nStatistical inference is based on potentially stale empirical data.\n");
        sb.append("Consider running a fresh MEASURE experiment to update the baseline.");
        return new WarningContent("BASELINE EXPIRED", sb.toString());
    }

    /**
     * Renders a warning for imminent expiration.
     */
    private static WarningContent renderExpiringImminently(
            ExpirationPolicy policy, ExpirationStatus.ExpiringImminently status) {
        String body = String.format("""
            Baseline expires in %s (on %s).
            Schedule a MEASURE experiment to refresh the baseline.""",
            formatDuration(status.remaining()),
            formatInstant(policy.expirationTime().orElse(null))
        );
        return new WarningContent("BASELINE EXPIRING IMMINENTLY", body);
    }

    /**
     * Renders an informational message for approaching expiration.
     */
    private static WarningContent renderExpiringSoon(
            ExpirationPolicy policy, ExpirationStatus.ExpiringSoon status) {
        String body = String.format("Baseline expires in %s (on %s).",
            formatDuration(status.remaining()),
            formatInstant(policy.expirationTime().orElse(null))
        );
        return new WarningContent("BASELINE EXPIRES SOON", body);
    }

    /**
     * Formats a duration into a human-readable string.
     *
     * @param duration the duration to format
     * @return a human-readable duration string
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "unknown";
        }

        long totalDays = duration.toDays();
        if (totalDays > 0) {
            return totalDays + " day" + (totalDays == 1 ? "" : "s");
        }

        long totalHours = duration.toHours();
        if (totalHours > 0) {
            return totalHours + " hour" + (totalHours == 1 ? "" : "s");
        }

        long totalMinutes = duration.toMinutes();
        if (totalMinutes > 0) {
            return totalMinutes + " minute" + (totalMinutes == 1 ? "" : "s");
        }

        return "less than a minute";
    }

    /**
     * Formats an instant into a human-readable date/time string.
     *
     * @param instant the instant to format
     * @return a formatted date/time string
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        return instant.atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
    }
}
