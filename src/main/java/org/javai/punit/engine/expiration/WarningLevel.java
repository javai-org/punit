package org.javai.punit.engine.expiration;

import org.javai.punit.model.ExpirationStatus;

/**
 * Verbosity level at which expiration warnings should be displayed.
 *
 * <p>This enum maps {@link ExpirationStatus} to the appropriate verbosity
 * level for display:
 * <ul>
 *   <li>{@link #ALWAYS}: Always shown (expired baselines)</li>
 *   <li>{@link #NORMAL}: Shown at normal and verbose levels (imminent expiration)</li>
 *   <li>{@link #VERBOSE}: Shown only at verbose level (expiring soon)</li>
 * </ul>
 */
public enum WarningLevel {
    
    /**
     * Warning is always shown regardless of verbosity settings.
     * Used for expired baselines.
     */
    ALWAYS,
    
    /**
     * Warning is shown at normal and verbose verbosity levels.
     * Used for baselines expiring imminently (≤10% remaining).
     */
    NORMAL,
    
    /**
     * Warning is shown only at verbose verbosity level.
     * Used for baselines expiring soon (≤25% remaining).
     */
    VERBOSE;
    
    /**
     * Determines the warning level for the given expiration status.
     *
     * @param status the expiration status
     * @return the warning level, or null if no warning should be shown
     */
    public static WarningLevel forStatus(ExpirationStatus status) {
        return switch (status) {
            case ExpirationStatus.Expired e -> ALWAYS;
            case ExpirationStatus.ExpiringImminently i -> NORMAL;
            case ExpirationStatus.ExpiringSoon s -> VERBOSE;
            case ExpirationStatus.Valid v -> null;
            case ExpirationStatus.NoExpiration n -> null;
        };
    }
    
    /**
     * Returns true if a warning with this level should be shown given
     * the specified verbosity setting.
     *
     * @param verbose whether verbose mode is enabled
     * @return true if the warning should be shown
     */
    public boolean shouldShow(boolean verbose) {
        return switch (this) {
            case ALWAYS -> true;
            case NORMAL -> true;  // Shown at both normal and verbose
            case VERBOSE -> verbose;  // Only shown when verbose
        };
    }
}

