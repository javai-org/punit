package org.javai.punit.experiment.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A diff-optimized projection of a {@link UseCaseResult}.
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
 * @see ResultProjectionBuilder
 * @see UseCaseResult#getDiffableContent(int)
 */
public record ResultProjection(
    int sampleIndex,
    Instant timestamp,
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
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        diffableLines = List.copyOf(diffableLines);
    }
}

