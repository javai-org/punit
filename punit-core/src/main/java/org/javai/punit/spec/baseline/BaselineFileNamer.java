package org.javai.punit.spec.baseline;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.util.HashUtils;

/**
 * Generates and parses baseline filenames.
 *
 * <p>Format: {@code {UseCaseName}.{MethodName}-{YYYYMMDD-HHMM}-{footprintHash}-{covHash1}-{covHash2}.yaml}
 *
 * <p>The filename includes:
 * <ul>
 *   <li>Use case name (sanitized)</li>
 *   <li>Experiment method name</li>
 *   <li>Timestamp (for chronological ordering when sorted alphabetically)</li>
 *   <li>Footprint hash (factors + covariate declaration)</li>
 *   <li>Covariate value hashes</li>
 * </ul>
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code ShoppingUseCase.measureSearch-20260114-1030-a1b2-c3d4-e5f6.yaml}</li>
 * </ul>
 */
public final class BaselineFileNamer {

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int HASH_LENGTH = 4;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault());

    /**
     * Generates the filename for a baseline with full format.
     *
     * <p>Format: {@code {UseCaseName}.{MethodName}-{YYYYMMDD-HHMM}-{footprintHash}-{covHashes}.yaml}
     *
     * @param useCaseName the use case name (will be sanitized)
     * @param methodName the experiment method name
     * @param timestamp the timestamp for ordering
     * @param footprintHash the footprint hash (8 chars)
     * @param covariateProfile the covariate profile with resolved values
     * @return the filename
     */
    public String generateFilename(
            String useCaseName,
            String methodName,
            Instant timestamp,
            String footprintHash,
            CovariateProfile covariateProfile) {

        Objects.requireNonNull(useCaseName, "useCaseName must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(footprintHash, "footprintHash must not be null");
        Objects.requireNonNull(covariateProfile, "covariateProfile must not be null");

        var sb = new StringBuilder();
        sb.append(sanitize(useCaseName));
        sb.append(".").append(sanitize(methodName));
        sb.append("-").append(TIMESTAMP_FORMAT.format(timestamp));
        sb.append("-").append(HashUtils.truncateHash(footprintHash, HASH_LENGTH));

        for (String hash : covariateProfile.computeValueHashes()) {
            sb.append("-").append(HashUtils.truncateHash(hash, HASH_LENGTH));
        }

        sb.append(".yaml");
        return sb.toString();
    }

    /**
     * Generates the filename for a baseline (legacy format without method name).
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
        sb.append("-").append(HashUtils.truncateHash(footprintHash, HASH_LENGTH));

        // Use value hashes so different covariate circumstances produce different filenames
        for (String hash : covariateProfile.computeValueHashes()) {
            sb.append("-").append(HashUtils.truncateHash(hash, HASH_LENGTH));
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
