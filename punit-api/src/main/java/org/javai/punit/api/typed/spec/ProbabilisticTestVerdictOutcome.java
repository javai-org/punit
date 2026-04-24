package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.FactorBundle;

/**
 * Lightweight verdict summary returned by a
 * {@link ProbabilisticTestSpec}'s
 * {@link Spec#conclude() conclude()} call.
 *
 * <p>Intentionally thinner than the full
 * {@code org.javai.punit.verdict.ProbabilisticTestVerdict} — this Stage 2
 * shape carries only the fields the new engine can populate with
 * placeholder statistics. Stage 4 replaces the statistics and Stage 5
 * bridges to the richer record for reporting.
 *
 * <p>Stage 3 extends the record with an optional
 * {@link LatencyVerdict}. When present, {@link #verdict()} reflects
 * the <em>projected</em> outcome selected by the spec's
 * {@link Dimension}; {@link #latencyVerdict()} exposes the latency
 * side independently.
 *
 * @param verdict the projected PASS / FAIL / INCONCLUSIVE chosen by
 *                the spec's {@code assertOn(Dimension)}
 * @param factors the factor bundle observed
 * @param successes number of passing samples
 * @param failures number of failing samples
 * @param threshold the threshold evaluated against
 * @param thresholdOrigin provenance of the threshold
 * @param warnings free-form warnings attached to the verdict (e.g.
 *                 "statistics pending Stage 4")
 * @param latencyVerdict optional latency-side verdict; present when
 *                      the spec declared a non-disabled
 *                      {@link org.javai.punit.api.typed.LatencySpec}
 */
public record ProbabilisticTestVerdictOutcome(
        Verdict verdict,
        FactorBundle factors,
        int successes,
        int failures,
        double threshold,
        ThresholdOrigin thresholdOrigin,
        List<String> warnings,
        Optional<LatencyVerdict> latencyVerdict) {

    public ProbabilisticTestVerdictOutcome {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(thresholdOrigin, "thresholdOrigin");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(latencyVerdict, "latencyVerdict");
        if (successes < 0 || failures < 0) {
            throw new IllegalArgumentException("successes and failures must be non-negative");
        }
        warnings = List.copyOf(warnings);
    }

    /**
     * Stage-2-compatible constructor for consumers that don't carry a
     * latency verdict — defaults {@code latencyVerdict} to
     * {@link Optional#empty()}.
     */
    public ProbabilisticTestVerdictOutcome(
            Verdict verdict,
            FactorBundle factors,
            int successes,
            int failures,
            double threshold,
            ThresholdOrigin thresholdOrigin,
            List<String> warnings) {
        this(verdict, factors, successes, failures, threshold, thresholdOrigin,
                warnings, Optional.empty());
    }

    public int total() {
        return successes + failures;
    }

    public double observedPassRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes / (double) t;
    }
}
