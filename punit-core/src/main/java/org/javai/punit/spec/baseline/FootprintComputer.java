package org.javai.punit.spec.baseline;

import java.util.Map;
import java.util.Objects;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.util.HashUtils;

/**
 * Computes the invocation footprint for baseline matching.
 *
 * <p>The footprint uniquely identifies the combination of:
 * <ol>
 *   <li>Use case identity</li>
 *   <li>Functional parameters (factors)</li>
 *   <li>Covariate declaration (names, not values)</li>
 * </ol>
 *
 * <p>Two baselines with the same footprint are candidates for matching.
 * The covariate values then determine which candidate is selected.
 */
public final class FootprintComputer {

    private static final int HASH_LENGTH = 8;

    /**
     * Computes the invocation footprint.
     *
     * @param useCaseId the use case identifier
     * @param factors the functional parameters (may be empty)
     * @param covariateDeclaration the declared covariates
     * @return an 8-character hex hash representing the footprint
     */
    public String computeFootprint(
            String useCaseId,
            Map<String, Object> factors,
            CovariateDeclaration covariateDeclaration) {
        
        Objects.requireNonNull(useCaseId, "useCaseId must not be null");
        Objects.requireNonNull(factors, "factors must not be null");
        Objects.requireNonNull(covariateDeclaration, "covariateDeclaration must not be null");

        var sb = new StringBuilder();
        sb.append("usecase:").append(useCaseId).append("\n");

        // Factors in sorted order for stability
        factors.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append("factor:")
                .append(e.getKey()).append("=").append(e.getValue()).append("\n"));

        // Covariate names in declaration order
        covariateDeclaration.allKeys().forEach(key ->
            sb.append("covariate:").append(key).append("\n"));

        return HashUtils.truncateHash(HashUtils.sha256(sb.toString()), HASH_LENGTH);
    }

    /**
     * Computes a footprint with no factors.
     *
     * @param useCaseId the use case identifier
     * @param covariateDeclaration the declared covariates
     * @return the footprint hash
     */
    public String computeFootprint(String useCaseId, CovariateDeclaration covariateDeclaration) {
        return computeFootprint(useCaseId, Map.of(), covariateDeclaration);
    }

    /**
     * Computes a footprint with no factors and no covariates.
     *
     * @param useCaseId the use case identifier
     * @return the footprint hash
     */
    public String computeFootprint(String useCaseId) {
        return computeFootprint(useCaseId, Map.of(), CovariateDeclaration.EMPTY);
    }

}

