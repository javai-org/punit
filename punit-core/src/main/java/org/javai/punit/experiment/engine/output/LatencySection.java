package org.javai.punit.experiment.engine.output;

import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.statistics.LatencyDistribution;

/**
 * Writes the {@code latency:} section to experiment YAML output.
 *
 * <p>Shared by both MEASURE and EXPLORE output writers. The section stores the
 * full sorted vector of successful-response latencies (the canonical baseline
 * storage per javai-R STATISTICAL-COMPANION v1.1 &sect;12.4), plus {@code mean}
 * and {@code max} for human reporting. It is omitted entirely when no latency
 * distribution is available.
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
        long[] sorted = latency.sortedLatenciesMs();
        Object[] values = new Object[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            values[i] = sorted[i];
        }
        builder.startObject("latency")
            .field("sampleCount", latency.sampleCount())
            .field("meanMs", latency.meanMs())
            .field("maxMs", latency.maxMs())
            .inlineArray("sortedLatenciesMs", values)
            .endObject();
    }
}
