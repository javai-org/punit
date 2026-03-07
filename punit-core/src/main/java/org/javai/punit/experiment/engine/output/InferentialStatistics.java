package org.javai.punit.experiment.engine.output;

import java.util.Map;
import org.javai.punit.experiment.engine.YamlBuilder;

/**
 * Inferential statistics for large-sample experiment output.
 *
 * <p>Used by MEASURE mode where sample sizes are large (1000+) and
 * inferential statistics provide meaningful insights:
 * <ul>
 *   <li>Standard error indicates precision of the estimate</li>
 *   <li>Confidence interval bounds the true population parameter</li>
 *   <li>CI lower bound becomes the derived minPassRate threshold</li>
 * </ul>
 *
 * <p>With 1000 samples and 90% observed success rate:
 * <ul>
 *   <li>SE ≈ 0.0095 (tight)</li>
 *   <li>95% CI ≈ [0.8814, 0.9186] (narrow range)</li>
 * </ul>
 *
 * <p>Compare with {@code DescriptiveStatistics} used by EXPLORE/OPTIMIZE,
 * which omits these inferential measures since they're unreliable with
 * small samples.
 *
 * @param observed the observed success rate (0.0 to 1.0)
 * @param standardError standard error of the proportion
 * @param ciLower lower bound of 95% confidence interval
 * @param ciUpper upper bound of 95% confidence interval
 * @param successes count of successful samples
 * @param failures count of failed samples
 * @param failureDistribution optional breakdown of failure types
 *
 * @see DescriptiveStatistics
 */
public record InferentialStatistics(
    double observed,
    double standardError,
    double ciLower,
    double ciUpper,
    int successes,
    int failures,
    Map<String, Integer> failureDistribution
) {
    /**
     * Creates inferential statistics without failure distribution.
     */
    public static InferentialStatistics of(
            double observed,
            double standardError,
            double ciLower,
            double ciUpper,
            int successes,
            int failures) {
        return new InferentialStatistics(
            observed, standardError, ciLower, ciUpper,
            successes, failures, Map.of()
        );
    }

    /**
     * Creates inferential statistics with failure distribution.
     */
    public static InferentialStatistics of(
            double observed,
            double standardError,
            double ciLower,
            double ciUpper,
            int successes,
            int failures,
            Map<String, Integer> failureDistribution) {
        return new InferentialStatistics(
            observed, standardError, ciLower, ciUpper,
            successes, failures,
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
     * Returns the derived minimum pass rate threshold.
     *
     * <p>The lower bound of the 95% confidence interval is used as a
     * statistically defensible threshold: we are 95% confident the
     * true pass rate is at least this value.
     *
     * @return the CI lower bound
     */
    public double derivedMinPassRate() {
        return ciLower;
    }

    /**
     * Writes these statistics to a YAML builder.
     *
     * <p>Output format:
     * <pre>
     * statistics:
     *   successRate:
     *     observed: 0.9000
     *     standardError: 0.0095
     *     confidenceInterval95: [0.8814, 0.9186]
     *   successes: 900
     *   failures: 100
     *   failureDistribution:  # only if non-empty
     *     TIMEOUT: 50
     *     VALIDATION_ERROR: 50
     * </pre>
     *
     * @param builder the YAML builder to write to
     */
    public void writeTo(YamlBuilder builder) {
        builder.startObject("statistics")
            .startObject("successRate")
                .field("observed", observed, "%.4f")
                .field("standardError", standardError, "%.4f")
                .formattedInlineArray("confidenceInterval95", "%.4f", ciLower, ciUpper)
            .endObject()
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
     * Writes the requirements section derived from these statistics.
     *
     * <p>Output format:
     * <pre>
     * requirements:
     *   minPassRate: 0.8814
     * </pre>
     *
     * @param builder the YAML builder to write to
     */
    public void writeRequirementsTo(YamlBuilder builder) {
        builder.startObject("requirements")
            .field("minPassRate", derivedMinPassRate(), "%.4f")
            .endObject();
    }
}
