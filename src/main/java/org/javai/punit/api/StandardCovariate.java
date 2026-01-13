package org.javai.punit.api;

/**
 * Standard covariates provided by PUnit for common contextual factors.
 *
 * <p>Covariates are contextual factors that may influence use case performance.
 * Declaring covariates enables PUnit to:
 * <ul>
 *   <li>Capture environmental conditions during MEASURE experiments</li>
 *   <li>Match baselines to compatible test execution contexts</li>
 *   <li>Warn when test conditions don't conform to baseline conditions</li>
 * </ul>
 *
 * <p>Each standard covariate has built-in resolution and matching strategies.
 *
 * @see UseCase#covariates()
 */
public enum StandardCovariate {

    /**
     * Weekday vs weekend classification.
     *
     * <p><strong>Resolution:</strong> Current date → "Mo-Fr" or "Sa-So"
     * <p><strong>Matching:</strong> Exact string match between baseline and test
     */
    WEEKDAY_VERSUS_WEEKEND("weekday_vs_weekend"),

    /**
     * Time of day window.
     *
     * <p><strong>Resolution:</strong> Experiment execution interval (start to end time with timezone)
     * <p><strong>Matching:</strong> Current time falls within baseline's recorded interval
     *
     * <p>The value is a time range (e.g., "14:30-14:45 Europe/London") representing
     * the window during which samples should ideally be taken for statistical robustness.
     */
    TIME_OF_DAY("time_of_day"),

    /**
     * System timezone.
     *
     * <p><strong>Resolution:</strong> System default timezone
     * <p><strong>Matching:</strong> Exact string match
     */
    TIMEZONE("timezone"),

    /**
     * Deployment region.
     *
     * <p><strong>Resolution:</strong> System property {@code punit.region} or
     * environment variable {@code PUNIT_REGION}
     * <p><strong>Matching:</strong> Case-insensitive string match
     */
    REGION("region");

    private final String key;

    StandardCovariate(String key) {
        this.key = key;
    }

    /**
     * Returns the stable string key used in baseline specs.
     *
     * <p>This key is used for:
     * <ul>
     *   <li>YAML serialization in spec files</li>
     *   <li>Footprint computation</li>
     *   <li>Covariate profile storage</li>
     * </ul>
     *
     * @return the stable string key
     */
    public String key() {
        return key;
    }
}

