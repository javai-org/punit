package org.javai.punit.experiment.engine.output;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.javai.punit.experiment.engine.YamlBuilder;

/**
 * Shared utilities for experiment output writers.
 *
 * <p>Provides common functionality used by all three experiment output writers
 * (MEASURE, EXPLORE, OPTIMIZE):
 * <ul>
 *   <li>Content fingerprint computation (SHA-256)</li>
 *   <li>Header section writing</li>
 *   <li>ISO timestamp formatting</li>
 * </ul>
 *
 * <p>This class supports the composition-based design where each experiment
 * type has its own writer that composes shared utilities rather than
 * inheriting from a common base class.
 */
public final class OutputUtilities {

    /** ISO-8601 instant formatter for timestamps. */
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private OutputUtilities() {
        // Static utility class
    }

    /**
     * Computes a SHA-256 fingerprint of the given content.
     *
     * <p>The fingerprint is used for integrity verification and change detection.
     * It should be computed on the complete YAML content (excluding the fingerprint
     * line itself) and appended as the final line.
     *
     * @param content the content to fingerprint
     * @return lowercase hex-encoded SHA-256 hash (64 characters)
     */
    public static String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Appends the fingerprint line to content and returns the complete output.
     *
     * @param contentWithoutFingerprint the YAML content without fingerprint
     * @return complete content with fingerprint appended
     */
    public static String appendFingerprint(String contentWithoutFingerprint) {
        String fingerprint = computeFingerprint(contentWithoutFingerprint);
        return contentWithoutFingerprint + "contentFingerprint: " + fingerprint + "\n";
    }

    /**
     * Writes the standard header section for experiment output.
     *
     * <p>The header includes:
     * <ul>
     *   <li>Comment describing the output</li>
     *   <li>Schema version</li>
     *   <li>Use case identifier</li>
     *   <li>Experiment identifier (optional)</li>
     *   <li>Generation timestamp</li>
     *   <li>Experiment class and method (optional)</li>
     * </ul>
     *
     * @param builder the YAML builder to write to
     * @param header the header data
     */
    public static void writeHeader(YamlBuilder builder, OutputHeader header) {
        builder.comment(header.commentLine1())
            .comment(header.commentLine2())
            .comment(header.commentLine3())
            .blankLine()
            .field("schemaVersion", header.schemaVersion())
            .field("useCaseId", header.useCaseId());

        if (header.experimentId() != null && !header.experimentId().isEmpty()) {
            builder.field("experimentId", header.experimentId());
        }

        builder.field("generatedAt", ISO_FORMATTER.format(header.generatedAt()));

        if (header.experimentClass() != null && !header.experimentClass().isEmpty()) {
            builder.field("experimentClass", header.experimentClass());
        }
        if (header.experimentMethod() != null && !header.experimentMethod().isEmpty()) {
            builder.field("experimentMethod", header.experimentMethod());
        }
    }

    /**
     * Header data for experiment output files.
     *
     * @param commentLine1 first comment line (e.g., "Empirical Baseline for UseCaseId")
     * @param commentLine2 second comment line (e.g., "Generated automatically by punit")
     * @param commentLine3 third comment line (e.g., "DO NOT EDIT")
     * @param schemaVersion schema version identifier
     * @param useCaseId use case identifier
     * @param experimentId optional experiment identifier
     * @param generatedAt generation timestamp
     * @param experimentClass optional fully qualified class name
     * @param experimentMethod optional method name
     */
    public record OutputHeader(
        String commentLine1,
        String commentLine2,
        String commentLine3,
        String schemaVersion,
        String useCaseId,
        String experimentId,
        Instant generatedAt,
        String experimentClass,
        String experimentMethod
    ) {
        /**
         * Creates a header for MEASURE/EXPLORE baseline output.
         */
        public static OutputHeader forBaseline(
                String useCaseId,
                String experimentId,
                Instant generatedAt,
                String experimentClass,
                String experimentMethod) {
            return new OutputHeader(
                "Empirical Baseline for " + useCaseId,
                "Generated automatically by punit experiment runner",
                "DO NOT EDIT - create a specification based on this baseline instead",
                "punit-spec-1",
                useCaseId,
                experimentId,
                generatedAt,
                experimentClass,
                experimentMethod
            );
        }

        /**
         * Creates a header for OPTIMIZE history output.
         */
        public static OutputHeader forOptimization(
                String useCaseId,
                String experimentId,
                Instant generatedAt) {
            return new OutputHeader(
                "Optimization History for " + useCaseId,
                "Primary output: the best value for the control factor",
                "Generated automatically by punit @OptimizeExperiment",
                "punit-optimize-1",
                useCaseId,
                experimentId,
                generatedAt,
                null,
                null
            );
        }
    }
}
