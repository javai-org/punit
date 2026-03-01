package org.javai.punit.experiment.engine.output;

import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.statistics.LatencyDistribution;

/**
 * Writes the {@code latency:} section to experiment YAML output.
 *
 * <p>Shared by both MEASURE and EXPLORE output writers. The latency section
 * is purely descriptive — observed percentiles from successful samples — and
 * is omitted entirely when no latency distribution is available.
 *
 * @see org.javai.punit.experiment.measure.MeasureOutputWriter
 * @see org.javai.punit.experiment.explore.ExploreOutputWriter
 */
public final class LatencySection {

    private LatencySection() {}

    /**
     * Writes the latency section to the builder if the baseline has a latency distribution.
     *
     * @param builder the YAML builder
     * @param baseline the empirical baseline
     */
    public static void writeTo(YamlBuilder builder, EmpiricalBaseline baseline) {
        if (!baseline.hasLatencyDistribution()) {
            return;
        }
        LatencyDistribution latency = baseline.getLatencyDistribution();
        builder.startObject("latency")
            .field("sampleCount", latency.sampleCount())
            .field("meanMs", latency.meanMs())
            .field("standardDeviationMs", latency.standardDeviationMs())
            .field("p50Ms", latency.p50Ms())
            .field("p90Ms", latency.p90Ms())
            .field("p95Ms", latency.p95Ms())
            .field("p99Ms", latency.p99Ms())
            .field("maxMs", latency.maxMs())
            .endObject();
    }
}
