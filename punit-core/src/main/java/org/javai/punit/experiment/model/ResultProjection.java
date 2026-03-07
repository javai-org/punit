package org.javai.punit.experiment.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.javai.punit.experiment.engine.ResultProjectionBuilder;

/**
 * A diff-optimized projection of a {@link org.javai.punit.contract.UseCaseOutcome}.
 *
 * <p>Designed for side-by-side comparison in diff tools. Each projection captures
 * the key observable aspects of a single sample execution: postcondition results,
 * the full content of the result, and optional failure detail.
 *
 * <p>When used with diff anchor lines, projections may vary in length between
 * samples — the anchors provide alignment at sample boundaries.
 *
 * @see ResultProjectionBuilder
 */
public record ResultProjection(
    int sampleIndex,
    String input,
    Map<String, String> postconditions,
    long executionTimeMs,
    String content,
    String failureDetail
) {
    /** Status value for postconditions that passed. */
    public static final String PASSED = "passed";

    /** Status value for postconditions that failed. */
    public static final String FAILED = "failed";

    /** Status value for postconditions that were not evaluated. */
    public static final String SKIPPED = "skipped";

    /**
     * Returns whether all postconditions passed.
     *
     * @return true if no postconditions failed
     */
    public boolean success() {
        return postconditions.values().stream().noneMatch(FAILED::equals);
    }

    /**
     * Compact constructor for validation and defensive copying.
     */
    public ResultProjection {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sampleIndex must be non-negative");
        }
        // Preserve insertion order with defensive copy
        postconditions = Map.copyOf(new LinkedHashMap<>(postconditions));
    }
}
