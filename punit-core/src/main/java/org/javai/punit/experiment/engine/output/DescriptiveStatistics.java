package org.javai.punit.experiment.engine.output;

import java.util.Map;
import org.javai.punit.experiment.engine.YamlBuilder;

/**
 * Descriptive statistics for small-sample experiment output.
 *
 * <p>Used by EXPLORE and OPTIMIZE modes where sample sizes are small (~20)
 * and inferential statistics (standard error, confidence intervals) would
 * be misleading due to high uncertainty.
 *
 * <p>This record captures only what was directly observed:
 * <ul>
 *   <li>Observed success rate</li>
 *   <li>Raw counts (successes, failures)</li>
 *   <li>Failure distribution (optional, for qualitative insight)</li>
 * </ul>
 *
 * <p>Compare with {@code InferentialStatistics} used by MEASURE mode,
 * which includes standard error and confidence intervals meaningful
 * with large samples (1000+).
 *
 * @param observed the observed success rate (0.0 to 1.0)
 * @param successes count of successful samples
 * @param failures count of failed samples
 * @param failureDistribution optional breakdown of failure types
 *
 * @see InferentialStatistics
 */
public record DescriptiveStatistics(
    double observed,
    int successes,
    int failures,
    Map<String, Integer> failureDistribution
) {
    /**
     * Creates descriptive statistics without failure distribution.
     */
    public static DescriptiveStatistics of(double observed, int successes, int failures) {
        return new DescriptiveStatistics(observed, successes, failures, Map.of());
    }

    /**
     * Creates descriptive statistics with failure distribution.
     */
    public static DescriptiveStatistics of(
            double observed,
            int successes,
            int failures,
            Map<String, Integer> failureDistribution) {
        return new DescriptiveStatistics(
            observed,
            successes,
            failures,
            failureDistribution != null ? failureDistribution : Map.of()
        );
    }

    /**
     * Total sample count.
     */
    public int sampleCount() {
        return successes + failures;
    }

    /**
     * Writes these statistics to a YAML builder.
     *
     * <p>Output format:
     * <pre>
     * statistics:
     *   observed: 0.7000
     *   successes: 14
     *   failures: 6
     *   failureDistribution:  # only if non-empty
     *     TIMEOUT: 3
     *     VALIDATION_ERROR: 3
     * </pre>
     *
     * @param builder the YAML builder to write to
     */
    public void writeTo(YamlBuilder builder) {
        builder.startObject("statistics")
            .field("observed", observed, "%.4f")
            .field("successes", successes)
            .field("failures", failures);

        if (failureDistribution != null && !failureDistribution.isEmpty()) {
            builder.startObject("failureDistribution");
            for (var entry : failureDistribution.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }

        builder.endObject();
    }

    /**
     * Writes compact statistics for iteration lists (OPTIMIZE mode).
     *
     * <p>Output format:
     * <pre>
     * statistics:
     *   sampleCount: 20
     *   observed: 0.7500
     *   successes: 15
     *   failures: 5
     * </pre>
     *
     * @param builder the YAML builder to write to
     */
    public void writeCompactTo(YamlBuilder builder) {
        builder.startObject("statistics")
            .field("sampleCount", sampleCount())
            .field("observed", observed, "%.4f")
            .field("successes", successes)
            .field("failures", failures)
            .endObject();
    }
}
