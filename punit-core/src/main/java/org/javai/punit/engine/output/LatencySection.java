package org.javai.punit.engine.output;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.spec.SampleSummary;

/**
 * Produces the normative {@code latency:} YAML block emitted by
 * EX04 (baseline spec), EX05 (exploration row), EX06 (optimize
 * iteration), and the descriptive latency dimension of RP01
 * verdict records.
 *
 * <p>Every emitted block carries the LT01 population-indicator
 * triple — {@code basis} (currently always {@code passing-samples}),
 * {@code contributingSamples}, and {@code totalSamples} — alongside
 * the four percentiles. The percentiles are computed from passing
 * samples only (per LT01); the indicator names that population so
 * a reader can verify it without external context.
 *
 * <p>When zero samples passed, no block is emitted —
 * {@link #blockFor(SampleSummary)} returns {@link Optional#empty()}.
 * The caller's containing structure (the YAML root, the iteration
 * entry, etc.) continues normally without a {@code latency:} key.
 *
 * <p>Pure — performs no I/O. The shape it returns is YAML-friendly
 * (a {@link LinkedHashMap} preserving insertion order) so callers
 * can drop it directly into the snakeyaml {@code dump} tree.
 */
public final class LatencySection {

    /** The only currently defined population basis. */
    public static final String BASIS_PASSING_SAMPLES = "passing-samples";

    private LatencySection() { }

    /**
     * Build the {@code latency:} block from a sample summary's
     * passing-only latency result. Returns empty when no samples
     * passed.
     */
    public static Optional<Map<String, Object>> blockFor(SampleSummary<?> summary) {
        return blockFor(summary.passingLatencyResult(),
                summary.successes(), summary.total());
    }

    /**
     * Build the {@code latency:} block from raw passing-only data.
     * Returns empty when {@code contributingSamples == 0}. Useful
     * for unit-testing the block shape without constructing a full
     * {@link SampleSummary}.
     */
    public static Optional<Map<String, Object>> blockFor(
            LatencyResult passingPercentiles, int contributingSamples, int totalSamples) {
        if (contributingSamples == 0) {
            return Optional.empty();
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("basis", BASIS_PASSING_SAMPLES);
        block.put("contributingSamples", contributingSamples);
        block.put("totalSamples", totalSamples);
        block.put("p50Ms", passingPercentiles.p50().toMillis());
        block.put("p90Ms", passingPercentiles.p90().toMillis());
        block.put("p95Ms", passingPercentiles.p95().toMillis());
        block.put("p99Ms", passingPercentiles.p99().toMillis());
        return Optional.of(block);
    }
}
