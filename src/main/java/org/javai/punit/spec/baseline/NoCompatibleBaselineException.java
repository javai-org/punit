package org.javai.punit.spec.baseline;

import java.util.List;
import org.javai.punit.ptest.engine.ProbabilisticTestConfigurationException;

/**
 * Exception thrown when no baseline matches the required footprint or configuration.
 *
 * <p>This typically occurs when:
 * <ul>
 *   <li>The covariate declarations have changed since the baseline was created</li>
 *   <li>The factors used in the test differ from the experiment</li>
 *   <li>No baseline exists for the use case</li>
 *   <li>CONFIGURATION covariates don't match any available baseline</li>
 * </ul>
 */
public class NoCompatibleBaselineException extends ProbabilisticTestConfigurationException {

    private final String useCaseId;
    private final String expectedFootprint;
    private final List<String> availableFootprints;
    private final boolean isConfigurationMismatch;

    /**
     * Creates a new exception for footprint mismatch.
     *
     * @param useCaseId the use case identifier
     * @param expectedFootprint the footprint computed at test time
     * @param availableFootprints the footprints available in existing baselines
     */
    public NoCompatibleBaselineException(
            String useCaseId,
            String expectedFootprint,
            List<String> availableFootprints) {
        super(buildMessage(useCaseId, expectedFootprint, availableFootprints, false));
        this.useCaseId = useCaseId;
        this.expectedFootprint = expectedFootprint;
        this.availableFootprints = List.copyOf(availableFootprints);
        this.isConfigurationMismatch = false;
    }

    /**
     * Creates a new exception for CONFIGURATION covariate mismatch.
     *
     * @param mismatchType type of mismatch (e.g., "CONFIGURATION")
     * @param expectedConfig the expected configuration values
     * @param availableConfigs the configurations available in baselines
     */
    public static NoCompatibleBaselineException configurationMismatch(
            String mismatchType,
            String expectedConfig,
            List<String> availableConfigs) {
        return new NoCompatibleBaselineException(mismatchType, expectedConfig, availableConfigs, true);
    }

    private NoCompatibleBaselineException(
            String useCaseIdOrType,
            String expectedFootprint,
            List<String> availableFootprints,
            boolean isConfigurationMismatch) {
        super(buildMessage(useCaseIdOrType, expectedFootprint, availableFootprints, isConfigurationMismatch));
        this.useCaseId = useCaseIdOrType;
        this.expectedFootprint = expectedFootprint;
        this.availableFootprints = List.copyOf(availableFootprints);
        this.isConfigurationMismatch = isConfigurationMismatch;
    }

    private static String buildMessage(
            String useCaseIdOrType,
            String expectedFootprint,
            List<String> availableFootprints,
            boolean isConfigurationMismatch) {
        var sb = new StringBuilder();
        
        if (isConfigurationMismatch) {
            sb.append("No baseline matches current CONFIGURATION covariates.\n\n");
            sb.append("Current configuration:\n  ").append(expectedFootprint).append("\n\n");
            
            if (availableFootprints.isEmpty()) {
                sb.append("No baselines found.\n");
            } else {
                sb.append("Available baselines have:\n");
                for (String config : availableFootprints) {
                    sb.append("  ").append(config).append("\n");
                }
            }
            
            sb.append("\nWhat to do:\n");
            sb.append("  • Comparing configurations? Use EXPLORE mode\n");
            sb.append("  • Committed to new config? Run MEASURE to establish baseline\n");
            sb.append("  • Wrong configuration? Check @CovariateSource methods\n");
        } else {
            sb.append("No baseline matches footprint '").append(expectedFootprint);
            sb.append("' for use case '").append(useCaseIdOrType).append("'. ");
            
            if (availableFootprints.isEmpty()) {
                sb.append("No baselines found for this use case. ");
            } else {
                sb.append("Available footprints: ").append(availableFootprints).append(". ");
            }
            
            sb.append("This may indicate covariate declarations have changed. ");
            sb.append("Run a MEASURE experiment to generate a compatible baseline.");
        }
        
        return sb.toString();
    }

    /**
     * Returns true if this is a CONFIGURATION covariate mismatch.
     */
    public boolean isConfigurationMismatch() {
        return isConfigurationMismatch;
    }

    /**
     * Returns the use case identifier.
     */
    public String getUseCaseId() {
        return useCaseId;
    }

    /**
     * Returns the expected footprint.
     */
    public String getExpectedFootprint() {
        return expectedFootprint;
    }

    /**
     * Returns the available footprints.
     */
    public List<String> getAvailableFootprints() {
        return availableFootprints;
    }
}

