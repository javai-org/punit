package org.javai.punit.power;

import java.util.Objects;
import java.util.function.Supplier;

import org.apache.commons.statistics.distribution.NormalDistribution;
import org.javai.punit.api.typed.spec.MeasureSpec;

/**
 * Authoring-time sample-size utility for the confidence-first
 * probabilistic-test pattern (PT03).
 *
 * <p>Given a baseline supplier and a desired minimum detectable
 * effect plus statistical power, computes the sample size at which
 * the one-proportion z-test (SC06) achieves that power against the
 * baseline rate.
 *
 * <p>Authors call this at spec-construction time and stamp the
 * computed sample count onto a template sampling, then bind factors
 * at the spec entry point:
 *
 * <pre>{@code
 * int n = PowerAnalysis.sampleSize(this::shoppingBaseline, 0.02, 0.80);
 * var sampling = samplingTemplate().samples(n);
 * return ProbabilisticTestSpec.testing(sampling, factors)
 *         .criterion(BernoulliPassRate.empirical())
 *         .build();
 * }</pre>
 *
 * <p>The default confidence level is {@value #DEFAULT_CONFIDENCE} —
 * matching the empirical-criterion default. Stage 4 lifts that to a
 * configurable parameter when the full resolver wires in.
 */
public final class PowerAnalysis {

    /** The default confidence (1 - α) used when none is specified. */
    public static final double DEFAULT_CONFIDENCE = 0.95;

    private static final NormalDistribution STANDARD_NORMAL = NormalDistribution.of(0, 1);

    /**
     * Stage-3.5 placeholder for the resolver-resolved observed rate.
     * The most conservative choice (maximum variance under the null)
     * — the formula's required sample size is largest when p = 0.5,
     * so this is a safe upper bound until Stage 4 plugs in the real
     * baseline-resolution machinery.
     */
    private static final double STAGE_3_5_PLACEHOLDER_BASELINE_RATE = 0.5;

    private PowerAnalysis() { }

    /**
     * Computes the sample size required to detect a difference of at
     * least {@code mde} from the resolved baseline rate at the given
     * statistical power, using the default confidence
     * ({@value #DEFAULT_CONFIDENCE}).
     *
     * <p>Stage 3.5 invokes the supplier once (so a misconfigured
     * baseline supplier surfaces immediately) but uses a placeholder
     * observed rate of 0.5 for the variance term. Stage 4 replaces
     * the placeholder with the real resolver that reads the
     * persisted baseline matching the MeasureSpec's use-case
     * identity + factor / covariate profile.
     *
     * @param baseline a supplier that yields the baseline MeasureSpec
     *                 — typically a method reference to an
     *                 {@code @Experiment} method
     * @param mde      the minimum detectable effect, in (0, 1)
     * @param power    the required statistical power, in (0, 1)
     * @return the required sample count
     * @throws NullPointerException     if {@code baseline} is null
     * @throws IllegalArgumentException if {@code mde} ∉ (0, 1) or
     *                                  {@code power} ∉ (0, 1) or the
     *                                  resolved baseline rate is
     *                                  incompatible with the
     *                                  requested MDE (i.e.
     *                                  {@code rate - mde ≤ 0} or
     *                                  {@code rate + mde ≥ 1})
     */
    public static int sampleSize(
            Supplier<MeasureSpec> baseline,
            double mde,
            double power) {
        Objects.requireNonNull(baseline, "baseline");
        validate(mde, power);

        // Invoke the supplier once — validates that the author wired up a
        // real baseline-producing method and not a thunk that will blow up
        // at evaluate-time. The returned MeasureSpec is unused under the
        // Stage-3.5 placeholder; Stage 4 reads its observed rate.
        MeasureSpec resolved = Objects.requireNonNull(
                baseline.get(),
                "baseline supplier returned null");

        double observedRate = STAGE_3_5_PLACEHOLDER_BASELINE_RATE;
        if (observedRate - mde <= 0.0 || observedRate + mde >= 1.0) {
            throw new IllegalArgumentException(
                    "baseline observed rate " + observedRate
                            + " is incompatible with mde=" + mde
                            + " — the [rate-mde, rate+mde] interval must lie in (0, 1)");
        }

        // One-proportion z-test sample-size formula (SC06):
        //   n = ((z_α + z_β)² × p(1-p)) / mde²
        double zAlpha = STANDARD_NORMAL.inverseCumulativeProbability(DEFAULT_CONFIDENCE);
        double zBeta = STANDARD_NORMAL.inverseCumulativeProbability(power);
        double numerator = (zAlpha + zBeta) * (zAlpha + zBeta)
                * observedRate * (1.0 - observedRate);
        double denominator = mde * mde;
        double n = numerator / denominator;

        // Reference resolved so any future verification of supplier identity
        // doesn't need to re-invoke it; documented absence of further use.
        Objects.requireNonNull(resolved);

        return (int) Math.ceil(n);
    }

    private static void validate(double mde, double power) {
        if (Double.isNaN(mde) || mde <= 0.0 || mde >= 1.0) {
            throw new IllegalArgumentException(
                    "mde must be in (0, 1), got " + mde);
        }
        if (Double.isNaN(power) || power <= 0.0 || power >= 1.0) {
            throw new IllegalArgumentException(
                    "power must be in (0, 1), got " + power);
        }
    }
}
