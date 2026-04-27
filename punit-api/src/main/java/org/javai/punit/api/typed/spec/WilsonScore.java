package org.javai.punit.api.typed.spec;

/**
 * Wilson-score confidence interval for a binomial proportion.
 *
 * <p>Used by {@link BernoulliPassRate}'s empirical path to wrap the
 * observed pass rate in a two-sided interval at a configured
 * confidence level. The criterion's verdict is {@link Verdict#PASS}
 * when the lower bound respects the baseline-derived threshold,
 * {@link Verdict#FAIL} when it does not. See SC01 / SC02 in the
 * orchestrator catalog for the design rationale.
 *
 * <p>This utility is self-contained — no statistics library dependency
 * — so {@code punit-api} can compute the interval without pulling in
 * {@code commons-statistics-distribution}. The standard-normal
 * inverse-CDF is approximated via Acklam's algorithm
 * (<a href="https://web.archive.org/web/20151030215612/http://home.online.no/~pjacklam/notes/invnorm/">
 * Beasley-Springer-Moro family</a>) which is accurate to better than
 * {@code 1.15e-9} across the full {@code (0, 1)} range — well within
 * the precision needed for verdict thresholds.
 */
public final class WilsonScore {

    private WilsonScore() { }

    /**
     * @return the Wilson-score lower bound on a binomial proportion
     * @param observed     the observed proportion in {@code [0, 1]}
     * @param sampleCount  the count of trials, {@code > 0}
     * @param confidence   the two-sided confidence level in
     *                     {@code (0, 1)} — typically {@code 0.95}
     */
    public static double lowerBound(double observed, int sampleCount, double confidence) {
        return interval(observed, sampleCount, confidence)[0];
    }

    /**
     * @return the Wilson-score upper bound on a binomial proportion
     * @see #lowerBound(double, int, double)
     */
    public static double upperBound(double observed, int sampleCount, double confidence) {
        return interval(observed, sampleCount, confidence)[1];
    }

    /**
     * @return a two-element array {@code [lower, upper]} carrying the
     *         Wilson-score interval bounds; spares callers a second
     *         pass through the same arithmetic when both bounds are
     *         needed (e.g. for emission to the verdict's detail map)
     */
    public static double[] interval(double observed, int sampleCount, double confidence) {
        validate(observed, sampleCount, confidence);
        double z = standardNormalCriticalValue(confidence);
        double n = sampleCount;
        double zSq = z * z;
        double denom = 1.0 + zSq / n;
        double centre = (observed + zSq / (2.0 * n)) / denom;
        double margin = z * Math.sqrt(observed * (1.0 - observed) / n
                + zSq / (4.0 * n * n)) / denom;
        return new double[] { centre - margin, centre + margin };
    }

    /**
     * The two-sided critical value {@code z*} of the standard normal
     * distribution at the given confidence level — i.e. the value
     * such that {@code P(-z* < Z < z*) = confidence} for {@code Z ~ N(0, 1)}.
     */
    static double standardNormalCriticalValue(double confidence) {
        // Two-sided: probability mass in each tail is (1 - confidence) / 2.
        // The critical value z* is the inverse-CDF at 1 - (1 - c)/2 = (1 + c)/2.
        return inverseStandardNormalCdf((1.0 + confidence) / 2.0);
    }

    private static void validate(double observed, int sampleCount, double confidence) {
        if (Double.isNaN(observed) || observed < 0.0 || observed > 1.0) {
            throw new IllegalArgumentException(
                    "observed must be in [0, 1], got " + observed);
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be > 0, got " + sampleCount);
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
    }

    // ── Inverse standard normal CDF — Acklam's algorithm ────────────
    //
    // Computes z such that Φ(z) = p for Φ the standard normal CDF.
    // Accuracy is ~1.15e-9 absolute error across (0, 1) — far better
    // than needed for the bound calculation. The constants below are
    // the published coefficients; do not modify without re-validating
    // accuracy.

    private static final double[] A = {
            -3.969683028665376e+01,  2.209460984245205e+02,
            -2.759285104469687e+02,  1.383577518672690e+02,
            -3.066479806614716e+01,  2.506628277459239e+00 };
    private static final double[] B = {
            -5.447609879822406e+01,  1.615858368580409e+02,
            -1.556989798598866e+02,  6.680131188771972e+01,
            -1.328068155288572e+01 };
    private static final double[] C = {
            -7.784894002430293e-03, -3.223964580411365e-01,
            -2.400758277161838e+00, -2.549732539343734e+00,
             4.374664141464968e+00,  2.938163982698783e+00 };
    private static final double[] D = {
             7.784695709041462e-03,  3.224671290700398e-01,
             2.445134137142996e+00,  3.754408661907416e+00 };
    private static final double LOWER_SPLIT = 0.02425;
    private static final double UPPER_SPLIT = 1.0 - LOWER_SPLIT;

    static double inverseStandardNormalCdf(double p) {
        if (Double.isNaN(p) || p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException(
                    "p must be in (0, 1), got " + p);
        }
        if (p < LOWER_SPLIT) {
            double q = Math.sqrt(-2.0 * Math.log(p));
            return (((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5])
                    / ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        }
        if (p > UPPER_SPLIT) {
            double q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            return -(((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5])
                    / ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        }
        double q = p - 0.5;
        double r = q * q;
        return (((((A[0] * r + A[1]) * r + A[2]) * r + A[3]) * r + A[4]) * r + A[5]) * q
                / (((((B[0] * r + B[1]) * r + B[2]) * r + B[3]) * r + B[4]) * r + 1.0);
    }
}
