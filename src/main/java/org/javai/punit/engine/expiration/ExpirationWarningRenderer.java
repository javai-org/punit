package org.javai.punit.engine.expiration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.javai.punit.model.ExpirationPolicy;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Renders expiration warnings for display in test output.
 *
 * <p>Produces formatted warning messages based on expiration status:
 * <ul>
 *   <li><strong>Expired</strong>: Prominent box format with remediation guidance</li>
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
     * Renders an expiration warning for the given status.
     *
     * @param spec the execution specification containing the expiration policy
     * @param status the expiration status
     * @return the rendered warning, or empty string if no warning is needed
     */
    public static String render(ExecutionSpecification spec, ExpirationStatus status) {
        if (status == null || !status.requiresWarning()) {
            return "";
        }

        return switch (status) {
            case ExpirationStatus.Expired expired -> 
                renderExpired(spec.getExpirationPolicy(), expired);
            case ExpirationStatus.ExpiringImminently imminent -> 
                renderExpiringImminently(spec.getExpirationPolicy(), imminent);
            case ExpirationStatus.ExpiringSoon soon -> 
                renderExpiringSoon(spec.getExpirationPolicy(), soon);
            default -> "";
        };
    }

    /**
     * Renders a prominent warning for an expired baseline.
     */
    private static String renderExpired(ExpirationPolicy policy, ExpirationStatus.Expired status) {
        return String.format("""
            ════════════════════════════════════════════════════════════
            ⚠️  BASELINE EXPIRED
            ════════════════════════════════════════════════════════════
            
            The baseline used for statistical inference has expired.
            
              Baseline created:   %s
              Validity period:    %d days
              Expiration date:    %s
              Expired:            %s ago
            
            Statistical inference is based on potentially stale empirical data.
            Consider running a fresh MEASURE experiment to update the baseline.
            
            ════════════════════════════════════════════════════════════
            """,
            formatInstant(policy.baselineEndTime()),
            policy.expiresInDays(),
            formatInstant(policy.expirationTime().orElse(null)),
            formatDuration(status.expiredAgo())
        );
    }

    /**
     * Renders a warning for imminent expiration.
     */
    private static String renderExpiringImminently(
            ExpirationPolicy policy, ExpirationStatus.ExpiringImminently status) {
        return String.format("""
            ⚠️  BASELINE EXPIRING IMMINENTLY
            
            Baseline expires in %s (on %s).
            Schedule a MEASURE experiment to refresh the baseline.
            """,
            formatDuration(status.remaining()),
            formatInstant(policy.expirationTime().orElse(null))
        );
    }

    /**
     * Renders an informational message for approaching expiration.
     */
    private static String renderExpiringSoon(
            ExpirationPolicy policy, ExpirationStatus.ExpiringSoon status) {
        return String.format("""
            ℹ️  Baseline expires soon
            
            Baseline expires in %s (on %s).
            """,
            formatDuration(status.remaining()),
            formatInstant(policy.expirationTime().orElse(null))
        );
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

