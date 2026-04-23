package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

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
 * @param verdict pass / fail / inconclusive
 * @param factors the factor bundle observed
 * @param successes number of passing samples
 * @param failures number of failing samples
 * @param threshold the threshold evaluated against
 * @param thresholdOrigin provenance of the threshold
 * @param warnings free-form warnings attached to the verdict (e.g.
 *                 "statistics pending Stage 4")
 */
public record ProbabilisticTestVerdictOutcome(
        Verdict verdict,
        FactorBundle factors,
        int successes,
        int failures,
        double threshold,
        ThresholdOrigin thresholdOrigin,
        List<String> warnings) {

    public ProbabilisticTestVerdictOutcome {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(thresholdOrigin, "thresholdOrigin");
        Objects.requireNonNull(warnings, "warnings");
        if (successes < 0 || failures < 0) {
            throw new IllegalArgumentException("successes and failures must be non-negative");
        }
        warnings = List.copyOf(warnings);
    }

    public int total() {
        return successes + failures;
    }

    public double observedPassRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes / (double) t;
    }
}
