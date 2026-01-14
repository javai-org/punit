package org.javai.punit.engine.covariate;

import java.util.Objects;
import java.util.regex.Pattern;

import org.javai.punit.model.CovariateProfile;

/**
 * Generates and parses baseline filenames.
 *
 * <p>Format: {@code {UseCaseName}-{footprintHash}.yaml}
 *
 * <p>The footprint hash encodes:
 * <ul>
 *   <li>Use case identity</li>
 *   <li>Functional parameters (factors)</li>
 *   <li>Covariate declaration (names only, not values)</li>
 * </ul>
 *
 * <p>Covariate <em>values</em> are stored in the YAML content, not the filename.
 * This ensures that running MEASURE multiple times with the same configuration
 * produces the same filename, allowing baseline updates rather than proliferation.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code ShoppingUseCase-a1b2c3d4.yaml}</li>
 * </ul>
 */
public final class BaselineFileNamer {

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int HASH_LENGTH = 4;

    /**
     * Generates the filename for a baseline.
     *
     * <p>The filename includes hashes for each covariate's key AND value.
     * This ensures that different environmental circumstances (different covariate values)
     * produce different baseline files, allowing probabilistic tests to select
     * the most appropriate baseline for their current environment.
     *
     * <p><b>Important:</b> The covariate profile must be resolved with stable timestamps
     * (experiment start/end time) to produce consistent filenames across a single
     * experiment run.
     *
     * @param useCaseName the use case name (will be sanitized)
     * @param footprintHash the footprint hash (8 chars)
     * @param covariateProfile the covariate profile with resolved values
     * @return the filename
     */
    public String generateFilename(
            String useCaseName,
            String footprintHash,
            CovariateProfile covariateProfile) {
        
        Objects.requireNonNull(useCaseName, "useCaseName must not be null");
        Objects.requireNonNull(footprintHash, "footprintHash must not be null");
        Objects.requireNonNull(covariateProfile, "covariateProfile must not be null");

        var sb = new StringBuilder();
        sb.append(sanitize(useCaseName));
        sb.append("-").append(truncateHash(footprintHash));

        // Use value hashes so different covariate circumstances produce different filenames
        for (String hash : covariateProfile.computeValueHashes()) {
            sb.append("-").append(truncateHash(hash));
        }

        sb.append(".yaml");
        return sb.toString();
    }

    /**
     * Generates the filename for a baseline without covariates.
     *
     * @param useCaseName the use case name
     * @param footprintHash the footprint hash
     * @return the filename
     */
    public String generateFilename(String useCaseName, String footprintHash) {
        return generateFilename(useCaseName, footprintHash, CovariateProfile.empty());
    }

    /**
     * Parses a baseline filename to extract components.
     *
     * @param filename the filename to parse
     * @return the parsed components
     * @throws IllegalArgumentException if the filename format is invalid
     */
    public ParsedFilename parse(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");

        // Remove .yaml/.yml extension
        String name = filename;
        if (name.endsWith(".yaml")) {
            name = name.substring(0, name.length() - 5);
        } else if (name.endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }

        String[] parts = name.split("-");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid baseline filename format: " + filename);
        }

        String useCaseName = parts[0];
        String footprintHash = parts[1];

        String[] covariateHashes = new String[parts.length - 2];
        System.arraycopy(parts, 2, covariateHashes, 0, covariateHashes.length);

        return new ParsedFilename(useCaseName, footprintHash, covariateHashes);
    }

    private String sanitize(String name) {
        return UNSAFE_CHARS.matcher(name).replaceAll("_");
    }

    private String truncateHash(String hash) {
        return hash.substring(0, Math.min(HASH_LENGTH, hash.length()));
    }

    /**
     * Parsed baseline filename components.
     *
     * @param useCaseName the sanitized use case name
     * @param footprintHash the footprint hash (truncated)
     * @param covariateHashes the covariate value hashes (truncated), in order
     */
    public record ParsedFilename(
            String useCaseName,
            String footprintHash,
            String[] covariateHashes
    ) {
        /**
         * Returns the number of covariates.
         */
        public int covariateCount() {
            return covariateHashes.length;
        }

        /**
         * Returns true if this baseline has covariates.
         */
        public boolean hasCovariates() {
            return covariateHashes.length > 0;
        }
    }
}

