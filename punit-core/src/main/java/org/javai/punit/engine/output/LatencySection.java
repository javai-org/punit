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

    /** Minimum contributing samples required to emit each percentile (LT01). */
    public static final int MIN_SAMPLES_P50 = 1;
    public static final int MIN_SAMPLES_P90 = 10;
    public static final int MIN_SAMPLES_P95 = 20;
    public static final int MIN_SAMPLES_P99 = 100;

    /**
     * Sentinel value carried in latency-percentile fields (e.g.
     * {@code p50Ms}, {@code p95Ms}, {@code p99Ms}, {@code maxMs}) to
     * mean "not reliably estimated" — the contributing-sample count
     * fell below this percentile's LT01 minimum, so no value is
     * emitted. Producers (the verdict adapter) write this; consumers
     * (text and HTML renderers) recognise it and substitute a dash
     * rather than the literal {@code -1}.
     */
    public static final long PERCENTILE_UNAVAILABLE_MS = -1L;

    private LatencySection() { }

    /**
     * Whether {@code contributingSamples} meets the LT01
     * minimum-sample threshold for the given percentile.
     *
     * @param percentileLabel one of {@code "p50"}, {@code "p90"},
     *                        {@code "p95"}, {@code "p99"}.
     * @param contributingSamples the count of passing samples
     *                            available for the percentile
     *                            computation.
     * @return {@code true} if the count meets or exceeds the
     *         minimum for that percentile per LT01's
     *         {@code n ≥ 1 / (1 − p)} rule.
     */
    public static boolean isPercentileEmittable(String percentileLabel, int contributingSamples) {
        return contributingSamples >= minimumSamplesFor(percentileLabel);
    }

    /** The LT01 minimum-contributing-samples threshold for a percentile label. */
    public static int minimumSamplesFor(String percentileLabel) {
        return switch (percentileLabel) {
            case "p50" -> MIN_SAMPLES_P50;
            case "p90" -> MIN_SAMPLES_P90;
            case "p95" -> MIN_SAMPLES_P95;
            case "p99" -> MIN_SAMPLES_P99;
            default -> throw new IllegalArgumentException(
                    "unknown percentile label: " + percentileLabel
                            + " (expected one of p50, p90, p95, p99)");
        };
    }

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
        // Per LT01, omit each percentile key when contributingSamples
        // is below that percentile's minimum (1 / 10 / 20 / 100 for
        // p50 / p90 / p95 / p99). The artefact carries only the
        // percentiles that can be estimated reliably.
        if (isPercentileEmittable("p50", contributingSamples)) {
            block.put("p50Ms", passingPercentiles.p50().toMillis());
        }
        if (isPercentileEmittable("p90", contributingSamples)) {
            block.put("p90Ms", passingPercentiles.p90().toMillis());
        }
        if (isPercentileEmittable("p95", contributingSamples)) {
            block.put("p95Ms", passingPercentiles.p95().toMillis());
        }
        if (isPercentileEmittable("p99", contributingSamples)) {
            block.put("p99Ms", passingPercentiles.p99().toMillis());
        }
        return Optional.of(block);
    }
}
