package org.javai.punit.engine.covariate;

import java.util.List;

import org.javai.punit.ptest.engine.ProbabilisticTestConfigurationException;

/**
 * Exception thrown when no baseline matches the required footprint.
 *
 * <p>This typically occurs when:
 * <ul>
 *   <li>The covariate declarations have changed since the baseline was created</li>
 *   <li>The factors used in the test differ from the experiment</li>
 *   <li>No baseline exists for the use case</li>
 * </ul>
 */
public class NoCompatibleBaselineException extends ProbabilisticTestConfigurationException {

    private final String useCaseId;
    private final String expectedFootprint;
    private final List<String> availableFootprints;

    /**
     * Creates a new exception.
     *
     * @param useCaseId the use case identifier
     * @param expectedFootprint the footprint computed at test time
     * @param availableFootprints the footprints available in existing baselines
     */
    public NoCompatibleBaselineException(
            String useCaseId,
            String expectedFootprint,
            List<String> availableFootprints) {
        super(buildMessage(useCaseId, expectedFootprint, availableFootprints));
        this.useCaseId = useCaseId;
        this.expectedFootprint = expectedFootprint;
        this.availableFootprints = List.copyOf(availableFootprints);
    }

    private static String buildMessage(
            String useCaseId,
            String expectedFootprint,
            List<String> availableFootprints) {
        var sb = new StringBuilder();
        sb.append("No baseline matches footprint '").append(expectedFootprint);
        sb.append("' for use case '").append(useCaseId).append("'. ");
        
        if (availableFootprints.isEmpty()) {
            sb.append("No baselines found for this use case. ");
        } else {
            sb.append("Available footprints: ").append(availableFootprints).append(". ");
        }
        
        sb.append("This may indicate covariate declarations have changed. ");
        sb.append("Run a MEASURE experiment to generate a compatible baseline.");
        
        return sb.toString();
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

