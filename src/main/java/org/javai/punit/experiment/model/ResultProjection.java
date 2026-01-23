package org.javai.punit.experiment.model;

import java.util.List;
import org.javai.punit.experiment.engine.ResultProjectionBuilder;

/**
 * A diff-optimized projection of a {@link org.javai.punit.contract.UseCaseOutcome}.
 *
 * <p>Designed for line-by-line comparison in diff tools. All projections
 * for the same use case have identical structure regardless of actual
 * content, using placeholders for missing or excess values.
 *
 * <h2>Structure Guarantees</h2>
 * <ul>
 *   <li>Results with fewer lines than max: padded with {@link #ABSENT}</li>
 *   <li>Results with exactly max lines: no placeholders</li>
 *   <li>Results with more than max lines: max content lines + truncation notice</li>
 * </ul>
 *
 * <p>Note: The truncation notice does not count toward {@code maxDiffableLines}.
 *
 * <p>Note: Timestamp is intentionally excluded from the projection because it
 * always differs between samples, creating noise in diffs. The timestamp remains
 * available in the underlying outcome for debugging purposes.
 *
 * @see ResultProjectionBuilder
 */
public record ResultProjection(
    int sampleIndex,
    long executionTimeMs,
    List<String> diffableLines
) {
    /**
     * Placeholder for values that don't exist in this result
     * but are expected based on maxDiffableLines configuration.
     */
    public static final String ABSENT = "<absent>";

    /**
     * Compact constructor for validation and defensive copying.
     */
    public ResultProjection {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sampleIndex must be non-negative");
        }
        diffableLines = List.copyOf(diffableLines);
    }
}

